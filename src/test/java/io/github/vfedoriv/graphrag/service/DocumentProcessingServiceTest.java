package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentUploadRepository documentUploadRepository;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private ExtractionRunRepository extractionRunRepository;
    @Mock
    private DocumentProcessingRunRepository documentProcessingRunRepository;
    @Mock
    private DocumentUploadService documentUploadService;
    @Mock
    private DocumentParsingService documentParsingService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;
    @Mock
    private ObjectProvider<EmbeddingClient> embeddingClientProvider;
    @Mock
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;
    @Mock
    private Environment environment;
    @Mock
    private GraphExtractionService graphExtractionService;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void orchestratesParsingChunkingAndEmbedding() throws Exception {
        AppProperties appProperties = props();
        ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(appProperties));
        EmbeddingClient embeddingClient = texts -> List.of(
            List.of(0.1, 0.2, 0.3),
            List.of(0.4, 0.5, 0.6)
        );
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setKnowledgeBaseId("kb-1");
        doc.setOriginalFilename("a.txt");
        doc.setContentType("text/plain");
        doc.setContentUri("file:///tmp/a.txt");
        doc.setProcessingDefaultsJson("{\"preserveLineBreaks\":true}");

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties));
        when(extractionRunRepository.hasCompletedRun("doc-1")).thenReturn(false);
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("chunk-one chunk-two".getBytes());
        when(documentParsingService.parse(org.mockito.Mockito.eq("a.txt"), org.mockito.Mockito.eq("text/plain"), any(byte[].class)))
            .thenReturn("abcdefghij01234567");
        when(embeddingClientProvider.orderedStream()).thenReturn(Stream.of(embeddingClient));
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1"))
            .thenReturn(List.of(new DocumentChunkNode(), new DocumentChunkNode()));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        List<String> savedRunStages = new java.util.ArrayList<>();
        when(documentProcessingRunRepository.save(any(DocumentProcessingRunNode.class))).thenAnswer(i -> {
            DocumentProcessingRunNode run = i.getArgument(0);
            savedRunStages.add(run.getStage());
            return run;
        });
        DocumentProcessingService service = new DocumentProcessingService(
            documentUploadRepository,
            documentChunkRepository,
            extractionRunRepository,
            documentProcessingRunRepository,
            documentUploadService,
            documentParsingService,
            chunkingService,
            new DocumentProcessingOptionsRegistry(),
            neo4jClient,
            embeddingClientProvider,
            embeddingModelProvider,
            environment,
            graphExtractionService,
            TestAiObservationService.noop(),
            knowledgeBaseService
        );
        DocumentUploadNode processed = service.process("doc-1", false, java.util.Map.of("preserveLineBreaks", false));

        assertThat(processed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(processed.getProcessedAt()).isNotNull();
        verify(documentChunkRepository, times(2)).save(any());
        ArgumentCaptor<DocumentProcessingRunNode> runCaptor = ArgumentCaptor.forClass(DocumentProcessingRunNode.class);
        verify(documentProcessingRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        assertThat(savedRunStages)
            .contains("PARSING", "CHUNKING", "EMBEDDING", "EXTRACTING_GRAPH", "COMPLETED");
        DocumentProcessingRunNode completedRun = runCaptor.getAllValues().getLast();
        assertThat(completedRun.getStatus()).isEqualTo(DocumentProcessingRunStatus.COMPLETED);
        assertThat(completedRun.isActiveCompleted()).isTrue();
        assertThat(completedRun.getSavedDefaultsJson()).contains("\"preserveLineBreaks\":true");
        assertThat(completedRun.getRequestedOptionsJson()).contains("\"preserveLineBreaks\":false");
        assertThat(completedRun.getEffectiveOptionsJson()).contains("\"preserveLineBreaks\":false");
        verify(documentProcessingRunRepository).deactivateOtherCompletedRuns("doc-1", completedRun.getId());
    }

    @Test
    void recordsFailedProcessingRunWithoutCreatingChunksOrExtraction() throws Exception {
        AppProperties appProperties = props();
        ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(appProperties));
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setKnowledgeBaseId("kb-1");
        doc.setOriginalFilename("a.txt");
        doc.setContentType("text/plain");
        doc.setContentUri("file:///tmp/a.txt");

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(extractionRunRepository.hasCompletedRun("doc-1")).thenReturn(false);
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("content".getBytes());
        when(documentParsingService.parse(org.mockito.Mockito.eq("a.txt"), org.mockito.Mockito.eq("text/plain"), any(byte[].class)))
            .thenThrow(new IllegalArgumentException("parse failed"));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentProcessingRunRepository.save(any(DocumentProcessingRunNode.class))).thenAnswer(i -> i.getArgument(0));
        DocumentProcessingService service = new DocumentProcessingService(
            documentUploadRepository,
            documentChunkRepository,
            extractionRunRepository,
            documentProcessingRunRepository,
            documentUploadService,
            documentParsingService,
            chunkingService,
            new DocumentProcessingOptionsRegistry(),
            neo4jClient,
            embeddingClientProvider,
            embeddingModelProvider,
            environment,
            graphExtractionService,
            TestAiObservationService.noop(),
            knowledgeBaseService
        );

        DocumentUploadNode failed = service.process("doc-1");

        assertThat(failed.getStatus()).isEqualTo(DocumentStatus.FAILED);
        ArgumentCaptor<DocumentProcessingRunNode> runCaptor = ArgumentCaptor.forClass(DocumentProcessingRunNode.class);
        verify(documentProcessingRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        DocumentProcessingRunNode failedRun = runCaptor.getAllValues().getLast();
        assertThat(failedRun.getStatus()).isEqualTo(DocumentProcessingRunStatus.FAILED);
        assertThat(failedRun.isActiveCompleted()).isFalse();
        assertThat(failedRun.getErrorMessage()).isEqualTo("parse failed");
        verify(documentChunkRepository, never()).save(any());
        verify(graphExtractionService, never()).extract(any(), any(), org.mockito.Mockito.anyBoolean());
        verify(documentProcessingRunRepository, never()).deactivateOtherCompletedRuns(any(), any());
    }

    @Test
    void keepsLatestSavedEntityAcrossStatusTransitions() throws Exception {
        AppProperties appProperties = props();
        ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(appProperties));
        EmbeddingClient embeddingClient = texts -> List.of(
            List.of(0.1, 0.2, 0.3),
            List.of(0.4, 0.5, 0.6)
        );
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setKnowledgeBaseId("kb-1");
        doc.setOriginalFilename("a.txt");
        doc.setContentType("text/plain");
        doc.setContentUri("file:///tmp/a.txt");

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties));
        when(extractionRunRepository.hasCompletedRun("doc-1")).thenReturn(false);
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("chunk-one chunk-two".getBytes());
        when(documentParsingService.parse(org.mockito.Mockito.eq("a.txt"), org.mockito.Mockito.eq("text/plain"), any(byte[].class)))
            .thenReturn("abcdefghij01234567");
        when(embeddingClientProvider.orderedStream()).thenReturn(Stream.of(embeddingClient));
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1"))
            .thenReturn(List.of(new DocumentChunkNode(), new DocumentChunkNode()));

        AtomicLong persistedVersion = new AtomicLong(-1);
        when(documentUploadRepository.save(any(DocumentUploadNode.class))).thenAnswer(invocation -> {
            DocumentUploadNode in = invocation.getArgument(0);
            long expected = persistedVersion.get();
            long actual = in.getVersion() == null ? -1 : in.getVersion();
            if (actual != expected) {
                throw new OptimisticLockingFailureException(
                    "stale version: expected=%d actual=%d".formatted(expected, actual)
                );
            }
            long next = expected + 1;
            persistedVersion.set(next);
            DocumentUploadNode saved = new DocumentUploadNode();
            saved.setId(in.getId());
            ReflectionTestUtils.setField(saved, "version", next);
            saved.setKnowledgeBaseId(in.getKnowledgeBaseId());
            saved.setOriginalFilename(in.getOriginalFilename());
            saved.setContentType(in.getContentType());
            saved.setSizeBytes(in.getSizeBytes());
            saved.setSha256(in.getSha256());
            saved.setContentUri(in.getContentUri());
            saved.setStatus(in.getStatus());
            saved.setUploadedAt(in.getUploadedAt());
            saved.setProcessedAt(in.getProcessedAt());
            saved.setErrorMessage(in.getErrorMessage());
            saved.setProcessingDefaultsJson(in.getProcessingDefaultsJson());
            saved.setProcessingDefaultsUpdatedAt(in.getProcessingDefaultsUpdatedAt());
            return saved;
        });
        when(documentProcessingRunRepository.save(any(DocumentProcessingRunNode.class))).thenAnswer(i -> i.getArgument(0));

        DocumentProcessingService service = new DocumentProcessingService(
            documentUploadRepository,
            documentChunkRepository,
            extractionRunRepository,
            documentProcessingRunRepository,
            documentUploadService,
            documentParsingService,
            chunkingService,
            new DocumentProcessingOptionsRegistry(),
            neo4jClient,
            embeddingClientProvider,
            embeddingModelProvider,
            environment,
            graphExtractionService,
            TestAiObservationService.noop(),
            knowledgeBaseService
        );

        DocumentUploadNode processed = service.process("doc-1");
        assertThat(processed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(processed.getProcessedAt()).isNotNull();
    }

    private AppProperties props() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 2, 10),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private AiProfileNode profile(AppProperties appProperties) {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(AiProfileService.DEFAULT_PROFILE_ID);
        profile.setBaseUrl(appProperties.model().baseUrl());
        profile.setApiKey(appProperties.model().apiKey());
        profile.setChatModel(appProperties.model().chatModel());
        profile.setEmbeddingModel(appProperties.model().embeddingModel());
        profile.setEmbeddingDimensions(appProperties.model().embeddingDimensions());
        profile.setTimeoutSeconds(60);
        profile.setMaxRetries(2);
        return profile;
    }
}
