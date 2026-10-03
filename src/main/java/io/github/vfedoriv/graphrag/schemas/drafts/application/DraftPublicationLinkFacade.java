package io.github.vfedoriv.graphrag.schemas.drafts.application;

import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftPublicationLink;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftRepository;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class DraftPublicationLinkFacade implements DraftPublicationLink {
    private final SchemaDraftLifecycleService lifecycle;
    private final SchemaDraftRepository repository;

    public DraftPublicationLinkFacade(SchemaDraftLifecycleService lifecycle, SchemaDraftRepository repository) {
        this.lifecycle = lifecycle;
        this.repository = repository;
    }

    @Override
    @RelationalTransactional
    public void complete(Completion completion) {
        SchemaDraftNode draft = lifecycle.requireOwned(completion.knowledgeBaseId(), completion.draftId());
        if (!Objects.equals(draft.getPersistenceVersion(), completion.persistenceVersion())
            || draft.getRevision() != completion.draftRevision()
            || !Objects.equals(draft.getCurrentAggregateId(), completion.aggregateRevisionId())) {
            throw new OptimisticLockingFailureException("Schema draft changed during publication completion");
        }
        draft.setStatus(SchemaDraftStatus.PUBLISHED);
        draft.setPublicationSchemaId(completion.schemaId());
        draft.setPublicationContentHash(completion.contentHash());
        draft.setUpdatedAt(completion.completedAt());
        repository.save(draft);
    }
}
