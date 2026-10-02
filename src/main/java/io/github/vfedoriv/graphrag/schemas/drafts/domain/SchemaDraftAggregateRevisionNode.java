package io.github.vfedoriv.graphrag.schemas.drafts.domain;

import io.github.vfedoriv.graphrag.domain.DiffBaselineType;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftAggregateRevisionNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String runId;
    private long revision;
    private String candidatesJson;
    private String conflictsJson;
    private String warningsJson;
    private String schemaJson;
    private String contentHash;
    private DiffBaselineType diffBaselineType;
    private String diffBaselineId;
    private String diffBaselineSchemaJson;
    private String diffBaselineContentHash;
    private boolean partial;
    private Instant createdAt;
}
