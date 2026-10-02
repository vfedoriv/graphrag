package io.github.vfedoriv.graphrag.schemas.publication.application;

import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftPublicationLink;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import org.springframework.stereotype.Service;

/** Publication checkpoints keep registry registration outside the completion transaction. */
@Service
public class PublicationCheckpointService {
    private final SchemaDraftPublicationRepository publications;
    private final DraftPublicationLink draftPublicationLink;

    public PublicationCheckpointService(
        SchemaDraftPublicationRepository publications, DraftPublicationLink draftPublicationLink
    ) {
        this.publications = publications;
        this.draftPublicationLink = draftPublicationLink;
    }

    @RelationalTransactional
    public SchemaDraftPublicationNode savePublicationIntent(SchemaDraftPublicationNode publication) {
        return publications.save(publication);
    }

    @RelationalTransactional
    public SchemaDraftPublicationNode completePublication(
        SchemaDraftPublicationNode publication, DraftPublicationLink.Completion completion
    ) {
        SchemaDraftPublicationNode saved = publications.save(publication);
        draftPublicationLink.complete(completion);
        return saved;
    }
}
