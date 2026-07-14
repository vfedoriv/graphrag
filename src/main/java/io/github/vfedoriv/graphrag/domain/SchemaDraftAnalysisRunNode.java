package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftAnalysisRun")
@Getter
@Setter
public class SchemaDraftAnalysisRunNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String draftId;
    private String knowledgeBaseId;
    private SchemaDraftAnalysisStatus status;
    private long draftRevision;
    private long guidanceRevision;
    private String guidanceFingerprint;
    private String sourceSnapshotJson;
    private String sourceMembershipFingerprint;
    private String aiProfileId;
    private long aiProfileRevision;
    private String promptRevision;
    private String candidateRevision;
    private String settingsFingerprint;
    private String snapshotFingerprint;
    private String claimedBy;
    private Instant claimedAt;
    private int totalSources;
    private int succeededSources;
    private int failedSources;
    private boolean currentResult;
    private String aggregateRevisionId;
    private String failureCategory;
    private boolean retryable;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
}
