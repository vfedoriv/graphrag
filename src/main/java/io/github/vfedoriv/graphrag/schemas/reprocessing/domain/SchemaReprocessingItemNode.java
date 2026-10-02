package io.github.vfedoriv.graphrag.schemas.reprocessing.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaReprocessingItemNode {
    private String id;
    private Long persistenceVersion;
    private String planId;
    private String documentId;
    private String documentSha256;
    private SchemaReprocessingItemStatus status;
    private String failureCategory;
    private boolean retryable;
    private int retryCount;
    private String priorItemId;
    private String claimedBy;
    private Instant claimedAt;
    private Instant claimUntil;
    private Instant startedAt;
    private Instant completedAt;
}
