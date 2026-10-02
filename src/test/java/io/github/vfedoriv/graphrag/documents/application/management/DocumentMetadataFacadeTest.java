package io.github.vfedoriv.graphrag.documents.application.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentMetadata;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentMetadataFacadeTest {

    private final DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
    private final DocumentMetadataFacade facade = new DocumentMetadataFacade(documents);

    @Test
    void findOwnedBatchDeduplicatesSkipsBlankIdsAndCapsOneScopedLookup() {
        List<String> requestedIds = new ArrayList<>();
        requestedIds.add("doc-0");
        requestedIds.add(" ");
        requestedIds.add(null);
        requestedIds.add("doc-0");
        for (int index = 1; index <= 128; index++) {
            requestedIds.add("doc-" + index);
        }

        DocumentUploadNode lastOwned = document("doc-127", "kb-1", "last.pdf", "application/pdf");
        DocumentUploadNode firstOwned = document("doc-0", "kb-1", "first.txt", "text/plain");
        firstOwned.setContentUri("private://secret");
        firstOwned.setSha256("private-hash");
        when(documents.findAllByIdInAndKnowledgeBaseId(anyList(), eq("kb-1")))
            .thenReturn(List.of(lastOwned, firstOwned));

        List<DocumentMetadata> result = facade.findOwnedBatch("kb-1", requestedIds);

        assertThat(result).containsExactly(
            new DocumentMetadata("doc-127", "last.pdf", "application/pdf"),
            new DocumentMetadata("doc-0", "first.txt", "text/plain")
        );

        List<String> expectedLookupIds = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            expectedLookupIds.add("doc-" + index);
        }
        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(documents, times(1)).findAllByIdInAndKnowledgeBaseId(idsCaptor.capture(), eq("kb-1"));
        assertThat(idsCaptor.getValue()).containsExactlyElementsOf(expectedLookupIds);
    }

    @Test
    void findOwnedBatchReturnsEmptyWithoutQueryWhenNoUsableIdsRemain() {
        assertThat(facade.findOwnedBatch("kb-1", List.of("", "  "))).isEmpty();
        assertThat(facade.findOwnedBatch("kb-1", null)).isEmpty();
        verify(documents, times(0)).findAllByIdInAndKnowledgeBaseId(anyList(), eq("kb-1"));
    }

    @Test
    void findOwnedBatchOmitsMissingAndForeignDocumentsFromScopedResults() {
        DocumentUploadNode owned = document("owned", "kb-1", "owned.txt", "text/plain");
        when(documents.findAllByIdInAndKnowledgeBaseId(List.of("missing", "foreign", "owned"), "kb-1"))
            .thenReturn(List.of(owned));

        assertThat(facade.findOwnedBatch("kb-1", List.of("missing", "foreign", "owned")))
            .containsExactly(new DocumentMetadata("owned", "owned.txt", "text/plain"));

        verify(documents).findAllByIdInAndKnowledgeBaseId(List.of("missing", "foreign", "owned"), "kb-1");
    }

    @Test
    void selectOwnedReturnsOnlyIdentifiersInRepositoryOrderAndCapsCandidates() {
        DocumentUploadNode newest = document("doc-new", "kb-1", "report.pdf", "application/pdf");
        DocumentUploadNode older = document("doc-old", "kb-1", "report.pdf", "application/pdf");
        when(documents.findByMetadata("kb-1", " report.pdf ", " application/pdf ", 200))
            .thenReturn(List.of(newest, older));

        assertThat(facade.selectOwned("kb-1", " report.pdf ", " application/pdf ", 201))
            .containsExactly("doc-new", "doc-old");
    }

    @Test
    void selectOwnedPreservesAnInRangeCandidateLimit() {
        when(documents.findByMetadata("kb-1", null, "text/plain", 17)).thenReturn(List.of());

        assertThat(facade.selectOwned("kb-1", null, "text/plain", 17)).isEmpty();

        verify(documents).findByMetadata("kb-1", null, "text/plain", 17);
    }

    @Test
    void selectOwnedRejectsNonPositiveCandidateLimitsBeforeQuerying() {
        assertThatThrownBy(() -> facade.selectOwned("kb-1", null, null, 0))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(documents);
    }

    private DocumentUploadNode document(String id, String knowledgeBaseId, String filename, String contentType) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setOriginalFilename(filename);
        document.setContentType(contentType);
        return document;
    }
}
