package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.CitationType;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.Evidence;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.AdvancedSearchCitationCatalog.Catalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedSearchCitationMetadataServiceTest {
    private final DocumentUploadRepository repository = mock(DocumentUploadRepository.class);
    private final AdvancedSearchCitationMetadataService service =
        new AdvancedSearchCitationMetadataService(repository);

    @Test
    void snapshotsOwnedMetadataAndKeepsItAfterCurrentDocumentChanges() {
        DocumentUploadNode document = document("doc-1", "contract.pdf", "application/pdf");
        when(repository.findAllByIdInAndKnowledgeBaseId(List.of("doc-1"), "kb-1"))
            .thenReturn(List.of(document));
        Catalog source = catalog("doc-1");

        Catalog enriched = service.enrich("kb-1", source);
        document.setOriginalFilename("replacement.pdf");
        document.setContentType("text/plain");

        assertThat(enriched.evidence().getFirst().sourceFilename()).isEqualTo("contract.pdf");
        assertThat(enriched.evidence().getFirst().sourceContentType()).isEqualTo("application/pdf");
        assertThat(enriched.evidence().getFirst().sourceDisplayLabel()).isEqualTo("contract.pdf");
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
    }

    private Catalog catalog(String documentId) {
        Evidence evidence = new Evidence(
            "E1", CitationType.TEXT_CHILD, "chunk-1", documentId, null,
            "processing-1", "chunker-1", null, "evidence", 1, 0.9);
        return new Catalog(List.of(evidence), List.of(), List.of(), Map.of());
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
