package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.repository.SchemaDraftStorageMutationRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.SchemaDraftGraphService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;

@Service
public class SchemaDraftStorageMutationService {
    private final SchemaDraftStorageMutationRepository repository;
    private final SchemaDraftGraphService graphService;

    public SchemaDraftStorageMutationService(
        SchemaDraftStorageMutationRepository repository, SchemaDraftGraphService graphService
    ) {
        this.repository = repository;
        this.graphService = graphService;
    }

    @GraphTransactional(propagation = Propagation.REQUIRES_NEW)
    public SchemaDraftStorageMutationNode begin(
        SchemaDraftStorageMutationType type, String draftId, String sourceId, String contentUri
    ) {
        Instant now = Instant.now();
        SchemaDraftStorageMutationNode mutation = new SchemaDraftStorageMutationNode();
        mutation.setId(UUID.randomUUID().toString());
        mutation.setType(type);
        mutation.setState(SchemaDraftStorageMutationState.PENDING);
        mutation.setDraftId(draftId);
        mutation.setSourceId(sourceId);
        mutation.setContentUri(contentUri);
        mutation.setCreatedAt(now);
        mutation.setUpdatedAt(now);
        SchemaDraftStorageMutationNode saved = repository.save(mutation);
        graphService.attach(draftId, "SchemaDraftStorageMutation", saved.getId());
        return saved;
    }

    @GraphTransactional(propagation = Propagation.REQUIRES_NEW)
    public void recordContent(String id, String contentUri) {
        SchemaDraftStorageMutationNode mutation = repository.findById(id).orElseThrow();
        mutation.setContentUri(contentUri);
        mutation.setUpdatedAt(Instant.now());
        repository.save(mutation);
    }

    @GraphTransactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String id) { transition(id, SchemaDraftStorageMutationState.COMPLETED, null); }

    @GraphTransactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(String id) { transition(id, SchemaDraftStorageMutationState.COMPENSATED, null); }

    @GraphTransactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String id, Exception exception) {
        SchemaDraftStorageMutationNode mutation = repository.findById(id).orElseThrow();
        mutation.setRetryCount(mutation.getRetryCount() + 1);
        mutation.setLastError(exception.getClass().getSimpleName());
        mutation.setUpdatedAt(Instant.now());
        repository.save(mutation);
    }

    private void transition(String id, SchemaDraftStorageMutationState state, String error) {
        SchemaDraftStorageMutationNode mutation = repository.findById(id).orElseThrow();
        Instant now = Instant.now();
        mutation.setState(state);
        mutation.setLastError(error);
        mutation.setUpdatedAt(now);
        mutation.setCompletedAt(now);
        repository.save(mutation);
    }
}
