package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvancedSearchRunNode {
    private String id;
    private String knowledgeBaseId;
    private String queryText;
    private AdvancedSearchRunStatus status;
    private AdvancedSearchRunStage stage;
    private int requestedEvidence;
    private boolean includeEvidenceText;
    private String settingsSnapshotJson;
    private String activeAiProfileId;
    private Long activeAiProfileRevision;
    private String schemaDefinitionId;
    private String schemaContentHash;
    private String schemaSnapshotJson;
    private int completedBranches;
    private int totalBranches;
    private int evidenceCount;
    private Instant cancellationRequestedAt;
    private String failureCategory;
    private String claimedBy;
    private Instant claimedAt;
    private Instant deadlineAt;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant expiresAt;
    private Long persistenceVersion;
}
