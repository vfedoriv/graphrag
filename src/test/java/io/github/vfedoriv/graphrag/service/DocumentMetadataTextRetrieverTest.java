package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.search.retrieval.application.DocumentMetadataTextRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.bootstrap.integration.search.SearchDocumentMetadataAdapter;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentMetadataFacade;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.SearchDocumentMetadata;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository.RawCandidate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentMetadataTextRetrieverTest {

    @Test
    void scopesRelationalMetadataAndChunkLookupToKnowledgeBase() {
        DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
        TextChunkRetrievalRepository chunks = mock(TextChunkRetrievalRepository.class);
        SearchDocumentMetadata metadata = new SearchDocumentMetadataAdapter(new DocumentMetadataFacade(documents));
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
        DocumentMetadataTextRetriever retriever = new DocumentMetadataTextRetriever(metadata, chunks);

        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result result = retriever.retrieve(
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

    @Test
    void reportsExpiredMetadataRetrievalWithoutReadingEitherBoundary() {
        SearchDocumentMetadata metadata = mock(SearchDocumentMetadata.class);
        TextChunkRetrievalRepository chunks = mock(TextChunkRetrievalRepository.class);
        DocumentMetadataTextRetriever retriever = new DocumentMetadataTextRetriever(metadata, chunks);

        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result result = retriever.retrieve(
            new Request(
                "kb-1",
                List.of(),
                new MetadataConstraints("contract.txt", null),
                3,
                true,
                Instant.now().minusSeconds(1)
            )
        );

        assertThat(result.diagnostics().status()).isEqualTo(Status.DEADLINE_EXCEEDED);
        assertThat(result.candidates()).isEmpty();
        verifyNoInteractions(metadata, chunks);
    }
}
