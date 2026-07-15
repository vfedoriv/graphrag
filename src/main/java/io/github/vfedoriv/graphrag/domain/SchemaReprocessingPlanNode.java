package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaReprocessingPlan")
@Getter
@Setter
public class SchemaReprocessingPlanNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String draftId;
    private String knowledgeBaseId;
    private String schemaId;
    private String schemaContentHash;
    private String aiProfileId;
    private long aiProfileRevision;
    private String processingOptionsJson;
    private String retryOfPlanId;
    private SchemaReprocessingPlanStatus status;
    private String claimedBy;
    private Instant claimedAt;
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
