package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository.RawCandidate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentMetadataTextRetrieverTest {

    @Test
    void scopesRelationalMetadataAndChunkLookupToKnowledgeBase() {
        DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
        TextChunkRetrievalRepository chunks = mock(TextChunkRetrievalRepository.class);
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        when(documents.findByMetadata("kb-1", "contract.txt", "text/plain", 3)).thenReturn(List.of(document));
        when(chunks.findOwnedDocumentChunks("kb-1", List.of("doc-1"), 3)).thenReturn(List.of(
            new RawCandidate(
                new SourceIdentity("chunk-1", "doc-1", 0, null, null, null, null, null, null, null),
                1.0,
                "contract body"
            )
        ));
        DocumentMetadataTextRetriever retriever = new DocumentMetadataTextRetriever(documents, chunks);

        io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result result = retriever.retrieve(
            new Request(
                "kb-1",
                List.of(),
                new MetadataConstraints("contract.txt", "text/plain"),
                3,
                true,
                Instant.now().plusSeconds(10)
            )
        );

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.candidates()).singleElement().satisfies(candidate ->
            assertThat(candidate.source().documentId()).isEqualTo("doc-1")
        );
        verify(documents).findByMetadata("kb-1", "contract.txt", "text/plain", 3);
        verify(chunks).findOwnedDocumentChunks("kb-1", List.of("doc-1"), 3);
    }
}
