package io.github.vfedoriv.graphrag.schemas.publication.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

public final class SchemaDraftPublicationDtos {
    private SchemaDraftPublicationDtos() { }
    public record ReadinessBlockingReason(String id, String category, String detail) { }

    public record PublicationReadinessResponse(
        boolean ready, long draftRevision, String aggregateRevisionId, String projectionContentHash,
        String targetName, int targetVersion, List<ReadinessBlockingReason> blockingReasons
    ) { }

    public record PublishDraftRequest(@PositiveOrZero long revision, @NotBlank String projectionContentHash) { }

    public record PublicationResponse(
        String publicationId, String draftId, String schemaId, long draftRevision,
        String publicationContentHash, String currentSchemaContentHash, boolean contentDrifted,
        boolean active, Instant publishedAt
    ) { }
}
