package io.github.vfedoriv.graphrag.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftStorageMutationRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchemaDraftStorageReconciliationServiceTest {

    @Test
    void compensatesStoredContentWhenSourceMetadataWasNotCommitted() throws Exception {
        SchemaDraftStorageMutationRepository mutationRepository = mock(SchemaDraftStorageMutationRepository.class);
        SchemaDraftSourceRepository sourceRepository = mock(SchemaDraftSourceRepository.class);
        SchemaDraftStorageMutationService mutationService = mock(SchemaDraftStorageMutationService.class);
        BinaryStorageService storageService = mock(BinaryStorageService.class);
        Path orphan = Files.createTempFile("draft-storage-mutation", ".bin");
        SchemaDraftStorageMutationNode mutation = pending(
            "mutation-store", SchemaDraftStorageMutationType.STORE, orphan);
        when(mutationRepository.findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState.PENDING))
            .thenReturn(List.of(mutation));
        when(sourceRepository.findById("source-1")).thenReturn(Optional.empty());
        when(storageService.resolvePath(orphan.toUri())).thenReturn(orphan);
        SchemaDraftStorageReconciliationService service = new SchemaDraftStorageReconciliationService(
            mutationRepository, sourceRepository, mutationService, storageService);

        service.reconcile();

        verify(storageService).delete(orphan.toUri());
        verify(mutationService).compensate("mutation-store");
        verify(mutationRepository).deleteCompletedBefore(any());
        Files.deleteIfExists(orphan);
    }

    @Test
    void completesRepeatedDeletionWhenContentIsAlreadyAbsent() {
        SchemaDraftStorageMutationRepository mutationRepository = mock(SchemaDraftStorageMutationRepository.class);
        SchemaDraftSourceRepository sourceRepository = mock(SchemaDraftSourceRepository.class);
        SchemaDraftStorageMutationService mutationService = mock(SchemaDraftStorageMutationService.class);
        BinaryStorageService storageService = mock(BinaryStorageService.class);
        Path absent = Path.of("/tmp", "absent-draft-source-" + System.nanoTime());
        SchemaDraftStorageMutationNode mutation = pending(
            "mutation-delete", SchemaDraftStorageMutationType.DELETE, absent);
        when(mutationRepository.findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState.PENDING))
            .thenReturn(List.of(mutation));
        when(storageService.resolvePath(absent.toUri())).thenReturn(absent);
        SchemaDraftStorageReconciliationService service = new SchemaDraftStorageReconciliationService(
            mutationRepository, sourceRepository, mutationService, storageService);

        service.reconcile();

        verify(mutationService).complete("mutation-delete");
        verify(mutationRepository).deleteCompletedBefore(any());
    }

    @Test
    void completesStoredContentWhenMatchingSourceMetadataExists() {
        SchemaDraftStorageMutationRepository mutationRepository = mock(SchemaDraftStorageMutationRepository.class);
        SchemaDraftSourceRepository sourceRepository = mock(SchemaDraftSourceRepository.class);
        SchemaDraftStorageMutationService mutationService = mock(SchemaDraftStorageMutationService.class);
        BinaryStorageService storageService = mock(BinaryStorageService.class);
        Path content = Path.of("/tmp", "committed-draft-source");
        SchemaDraftStorageMutationNode mutation = pending(
            "mutation-committed", SchemaDraftStorageMutationType.STORE, content);
        SchemaDraftSourceNode source = new SchemaDraftSourceNode();
        source.setId("source-1");
        source.setContentUri(content.toUri().toString());
        when(mutationRepository.findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState.PENDING))
            .thenReturn(List.of(mutation));
        when(sourceRepository.findById("source-1")).thenReturn(Optional.of(source));
        SchemaDraftStorageReconciliationService service = new SchemaDraftStorageReconciliationService(
            mutationRepository, sourceRepository, mutationService, storageService);

        service.reconcile();

        verify(mutationService).complete("mutation-committed");
        verify(mutationRepository).deleteCompletedBefore(any());
    }

    private SchemaDraftStorageMutationNode pending(
        String id, SchemaDraftStorageMutationType type, Path content
    ) {
        SchemaDraftStorageMutationNode mutation = new SchemaDraftStorageMutationNode();
        mutation.setId(id);
        mutation.setType(type);
        mutation.setState(SchemaDraftStorageMutationState.PENDING);
        mutation.setDraftId("draft-1");
        mutation.setSourceId("source-1");
        mutation.setContentUri(content.toUri().toString());
        return mutation;
    }
}
