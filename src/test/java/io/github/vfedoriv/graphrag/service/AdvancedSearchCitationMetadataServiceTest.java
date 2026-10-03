package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.bootstrap.integration.search.SearchDocumentMetadataAdapter;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentMetadataFacade;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.search.answering.application.AdvancedSearchCitationMetadataService;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.CitationType;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.Evidence;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchCitationCatalog.Catalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedSearchCitationMetadataServiceTest {
    private final DocumentUploadRepository repository = mock(DocumentUploadRepository.class);
    private final SearchDocumentMetadataAdapter metadataAccess = new SearchDocumentMetadataAdapter(
        new DocumentMetadataFacade(repository)
    );
    private final AdvancedSearchCitationMetadataService service =
        new AdvancedSearchCitationMetadataService(metadataAccess);

    @Test
    void snapshotsOwnedMetadataAndKeepsItAfterCurrentDocumentChanges() {
        DocumentUploadNode document = document("doc-1", "contract.pdf", "application/pdf");
        when(repository.findAllByIdInAndKnowledgeBaseId(List.of("doc-1"), "kb-1"))
            .thenReturn(List.of(document));
        Catalog source = repeatedCatalog("doc-1");

        Catalog enriched = service.enrich("kb-1", source);
        document.setOriginalFilename("replacement.pdf");
        document.setContentType("text/plain");

        assertThat(enriched.evidence().getFirst().sourceFilename()).isEqualTo("contract.pdf");
        assertThat(enriched.evidence().getFirst().sourceContentType()).isEqualTo("application/pdf");
        assertThat(enriched.evidence().getFirst().sourceDisplayLabel()).isEqualTo("contract.pdf");
        assertThat(enriched.contexts().getFirst().sourceFilename()).isEqualTo("contract.pdf");
        verify(repository, times(1)).findAllByIdInAndKnowledgeBaseId(List.of("doc-1"), "kb-1");
    }

    @Test
    void usesDeterministicFallbackWhenDocumentWasDeletedBeforeAssemblyCompletes() {
        when(repository.findAllByIdInAndKnowledgeBaseId(List.of("doc-missing"), "kb-1"))
            .thenReturn(List.of());

        Catalog enriched = service.enrich("kb-1", catalog("doc-missing"));
        Evidence evidence = enriched.evidence().getFirst();

        assertThat(evidence.sourceFilename()).isEqualTo("document-doc-missing");
        assertThat(evidence.sourceContentType()).isNull();
        assertThat(evidence.sourceDisplayLabel()).isEqualTo("document-doc-missing");
        assertThat(enriched.metadataWarnings()).containsExactly("SOURCE_METADATA_UNAVAILABLE");
        verify(repository).findAllByIdInAndKnowledgeBaseId(List.of("doc-missing"), "kb-1");
    }

    private Catalog catalog(String documentId) {
        Evidence evidence = evidence("E1", "chunk-1", documentId);
        return new Catalog(List.of(evidence), List.of(), List.of(), Map.of());
    }

    private Catalog repeatedCatalog(String documentId) {
        Evidence evidence = evidence("E1", "chunk-1", documentId);
        Evidence context = evidence("E2", "chunk-2", documentId);
        return new Catalog(List.of(evidence), List.of(context), List.of(), Map.of());
    }

    private Evidence evidence(String citationId, String chunkId, String documentId) {
        return new Evidence(
            citationId, CitationType.TEXT_CHILD, chunkId, documentId, null,
            "processing-1", "chunker-1", null, "evidence", 1, 0.9);
    }

    private DocumentUploadNode document(String id, String filename, String contentType) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId("kb-1");
        document.setOriginalFilename(filename);
        document.setContentType(contentType);
        return document;
    }
}
