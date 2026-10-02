package io.github.vfedoriv.graphrag.schemas.publication.application;

import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.schemas.publication.contracts.PublicationFacts;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationStatus;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PublicationFactsFacade implements PublicationFacts {
    private final SchemaDraftPublicationRepository publications;

    public PublicationFactsFacade(SchemaDraftPublicationRepository publications) {
        this.publications = publications;
    }

    @Override
    @RelationalTransactional(readOnly = true)
    public Optional<Publication> findByDraftId(String draftId) {
        return publications.findByDraftId(draftId).map(this::snapshot);
    }

    @Override
    @RelationalTransactional(readOnly = true)
    public Optional<Publication> findBySchemaId(String schemaId) {
        return publications.findBySchemaId(schemaId).map(this::snapshot);
    }

    private Publication snapshot(SchemaDraftPublicationNode value) {
        return new Publication(value.getId(), value.getDraftId(), value.getKnowledgeBaseId(), value.getSchemaId(),
            value.getDraftRevision(), value.getAggregateRevisionId(), value.getProjectionContentHash(),
            value.getTargetIdentity(), value.getStatus() == SchemaDraftPublicationStatus.COMPLETED,
            value.getCreatedAt());
    }
}
