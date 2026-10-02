package io.github.vfedoriv.graphrag.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftStorageMutationService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftStorageReconciliationService;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftStorageMutationRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftBinaryStorage;
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
        DraftBinaryStorage storageService = mock(DraftBinaryStorage.class);
        Path orphan = Files.createTempFile("draft-storage-mutation", ".bin");
        SchemaDraftStorageMutationNode mutation = pending(
            "mutation-store", SchemaDraftStorageMutationType.STORE, orphan);
        when(mutationRepository.findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState.PENDING))
            .thenReturn(List.of(mutation));
        when(sourceRepository.findById("source-1")).thenReturn(Optional.empty());
        when(storageService.exists(orphan.toUri())).thenReturn(true);
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
        DraftBinaryStorage storageService = mock(DraftBinaryStorage.class);
        Path absent = Path.of("/tmp", "absent-draft-source-" + System.nanoTime());
        SchemaDraftStorageMutationNode mutation = pending(
            "mutation-delete", SchemaDraftStorageMutationType.DELETE, absent);
        when(mutationRepository.findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState.PENDING))
            .thenReturn(List.of(mutation));
        when(storageService.exists(absent.toUri())).thenReturn(false);
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
        DraftBinaryStorage storageService = mock(DraftBinaryStorage.class);
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
