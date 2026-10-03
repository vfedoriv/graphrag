package io.github.vfedoriv.graphrag.schemas.drafts.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftSourceResultNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String runId;
    private String sourceId;
    private long sourceRevision;
    private String sourceSha256;
    private String reuseKey;
    private SchemaDraftSourceResultStatus status;
    private boolean reused;
    private String candidatesJson;
    private String aliasesJson;
    private String failureCategory;
    private String failureCode;
    private boolean retryable;
    private int chunkCount;
    private Instant createdAt;
    private Instant completedAt;
}
