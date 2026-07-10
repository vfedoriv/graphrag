package io.github.vfedoriv.graphrag.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationState;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationType;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentStorageMutationRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentStorageReconciliationServiceTest {
    @Test
    void compensatesStoredBinaryWhenMetadataWasNotCommitted() throws Exception {
        DocumentStorageMutationRepository mutationRepository = mock(DocumentStorageMutationRepository.class);
        DocumentUploadRepository documentUploadRepository = mock(DocumentUploadRepository.class);
        DocumentStorageMutationService mutationService = mock(DocumentStorageMutationService.class);
        BinaryStorageService binaryStorageService = mock(BinaryStorageService.class);
        DocumentStorageMutationNode mutation = new DocumentStorageMutationNode();
        mutation.setId("mutation-1");
        mutation.setType(DocumentStorageMutationType.STORE);
        mutation.setState(DocumentStorageMutationState.PENDING);
        mutation.setDocumentId("doc-1");
        Path orphan = Files.createTempFile("storage-mutation", ".bin");
        mutation.setContentUri(orphan.toUri().toString());
        when(mutationRepository.findByStateOrderByCreatedAtAsc(DocumentStorageMutationState.PENDING)).thenReturn(List.of(mutation));
        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.empty());
        when(binaryStorageService.resolvePath(orphan.toUri())).thenReturn(orphan);
        when(mutationRepository.deleteCompletedBefore(any())).thenReturn(0L);
        DocumentStorageReconciliationService service = new DocumentStorageReconciliationService(
            mutationRepository, documentUploadRepository, mutationService, binaryStorageService,
            mock(GraphArtifactCleanupService.class), new SimpleMeterRegistry()
        );

        service.reconcilePendingMutations();

        verify(binaryStorageService).delete(orphan.toUri());
        verify(mutationService).compensate("mutation-1");
        Files.deleteIfExists(orphan);
    }

    @Test
    void completesPendingDocumentDeletionAfterStorageCleanup() {
        DocumentStorageMutationRepository mutationRepository = mock(DocumentStorageMutationRepository.class);
        DocumentUploadRepository documentUploadRepository = mock(DocumentUploadRepository.class);
        DocumentStorageMutationService mutationService = mock(DocumentStorageMutationService.class);
        DocumentStorageMutationNode mutation = pending("mutation-delete", DocumentStorageMutationType.DELETE, "doc-1", null);
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        GraphArtifactCleanupService cleanupService = mock(GraphArtifactCleanupService.class);
        when(mutationRepository.findByStateOrderByCreatedAtAsc(DocumentStorageMutationState.PENDING)).thenReturn(List.of(mutation));
        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(document));
        when(mutationRepository.deleteCompletedBefore(any())).thenReturn(0L);
        DocumentStorageReconciliationService service = new DocumentStorageReconciliationService(
            mutationRepository, documentUploadRepository, mutationService, mock(BinaryStorageService.class),
            cleanupService, new SimpleMeterRegistry()
        );

        service.reconcilePendingMutations();

        verify(cleanupService).cleanupDocumentArtifacts("doc-1");
        verify(documentUploadRepository).delete(document);
        verify(mutationService).complete("mutation-delete");
    }

    @Test
    void completesReplacementCleanupWithoutDeletingTheDocument() throws Exception {
        DocumentStorageMutationRepository mutationRepository = mock(DocumentStorageMutationRepository.class);
        DocumentUploadRepository documentUploadRepository = mock(DocumentUploadRepository.class);
        DocumentStorageMutationService mutationService = mock(DocumentStorageMutationService.class);
        BinaryStorageService binaryStorageService = mock(BinaryStorageService.class);
        Path replacedContent = Files.createTempFile("replaced-content", ".bin");
        DocumentStorageMutationNode mutation = pending(
            "mutation-replace", DocumentStorageMutationType.DELETE_REPLACED_CONTENT, "doc-1", replacedContent.toUri().toString()
        );
        when(mutationRepository.findByStateOrderByCreatedAtAsc(DocumentStorageMutationState.PENDING)).thenReturn(List.of(mutation));
        when(binaryStorageService.resolvePath(replacedContent.toUri())).thenReturn(replacedContent);
        when(mutationRepository.deleteCompletedBefore(any())).thenReturn(0L);
        DocumentStorageReconciliationService service = new DocumentStorageReconciliationService(
            mutationRepository, documentUploadRepository, mutationService, binaryStorageService,
            mock(GraphArtifactCleanupService.class), new SimpleMeterRegistry()
        );

        service.reconcilePendingMutations();

        verify(binaryStorageService).delete(replacedContent.toUri());
        verify(documentUploadRepository, org.mockito.Mockito.never()).delete(any(DocumentUploadNode.class));
        verify(mutationService).complete("mutation-replace");
        Files.deleteIfExists(replacedContent);
    }

    private DocumentStorageMutationNode pending(String id, DocumentStorageMutationType type, String documentId, String contentUri) {
        DocumentStorageMutationNode mutation = new DocumentStorageMutationNode();
        mutation.setId(id);
        mutation.setType(type);
        mutation.setState(DocumentStorageMutationState.PENDING);
        mutation.setDocumentId(documentId);
        mutation.setContentUri(contentUri);
        return mutation;
    }
}
