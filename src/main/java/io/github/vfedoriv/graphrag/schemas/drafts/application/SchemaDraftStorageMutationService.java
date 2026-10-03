package io.github.vfedoriv.graphrag.schemas.drafts.application;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftStorageMutationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
public class SchemaDraftStorageMutationService {
    private final SchemaDraftStorageMutationRepository repository;

    public SchemaDraftStorageMutationService(SchemaDraftStorageMutationRepository repository) {
        this.repository = repository;
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
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
        return repository.save(mutation);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void recordContent(String id, String contentUri) {
        SchemaDraftStorageMutationNode mutation = repository.findById(id).orElseThrow();
        mutation.setContentUri(contentUri);
        mutation.setUpdatedAt(Instant.now());
        repository.save(mutation);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String id) { transition(id, SchemaDraftStorageMutationState.COMPLETED, null); }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(String id) { transition(id, SchemaDraftStorageMutationState.COMPENSATED, null); }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
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
