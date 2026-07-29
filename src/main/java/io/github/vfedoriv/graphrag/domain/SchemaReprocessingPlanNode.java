package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaReprocessingPlanNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String knowledgeBaseId;
    private String schemaId;
    private String schemaContentHash;
    private String aiProfileId;
    private long aiProfileRevision;
    private String processingOptionsJson;
    private String retryOfPlanId;
    private int retryCount;
    private SchemaReprocessingPlanStatus status;
    private String claimedBy;
    private Instant claimedAt;
    private Instant claimUntil;
    private int totalDocuments;
    private int queuedDocuments;
    private int runningDocuments;
    private int succeededDocuments;
    private int failedDocuments;
    private int staleDocuments;
    private int blockedDocuments;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
}
