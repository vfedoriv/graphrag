package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentUploadRepository documentUploadRepository;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private DocumentUploadService documentUploadService;
    @Mock
    private DocumentParsingService documentParsingService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;
    @Mock
    private ObjectProvider<EmbeddingClient> embeddingClientProvider;
    @Mock
    private GraphExtractionService graphExtractionService;

    @Test
    void orchestratesParsingChunkingAndEmbedding() throws Exception {
        ChunkingService chunkingService = new ChunkingService(props());
        EmbeddingClient embeddingClient = texts -> List.of(
            List.of(0.1, 0.2, 0.3),
            List.of(0.4, 0.5, 0.6)
        );
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setOriginalFilename("a.txt");
        doc.setContentType("text/plain");
        doc.setContentUri("file:///tmp/a.txt");

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("chunk-one chunk-two".getBytes());
        when(documentParsingService.parse("a.txt", "text/plain", "chunk-one chunk-two".getBytes()))
            .thenReturn("abcdefghij01234567");
        when(embeddingClientProvider.getIfAvailable()).thenReturn(embeddingClient);
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1"))
            .thenReturn(List.of(new DocumentChunkNode(), new DocumentChunkNode()));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        DocumentProcessingService service = new DocumentProcessingService(
            documentUploadRepository,
            documentChunkRepository,
            documentUploadService,
            documentParsingService,
            chunkingService,
            neo4jClient,
            props(),
            embeddingClientProvider,
            graphExtractionService
        );
        DocumentUploadNode processed = service.process("doc-1");

        assertThat(processed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(processed.getProcessedAt()).isNotNull();
        verify(documentChunkRepository, times(2)).save(any());
    }

    private AppProperties props() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 2, 10),
            new AppProperties.Query(200, 15, true, List.of("CREATE")),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
