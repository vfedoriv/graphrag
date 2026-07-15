package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftEvaluationOutcome")
@Getter
@Setter
public class SchemaDraftEvaluationOutcomeNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String runId;
    private String draftId;
    private String documentId;
    private String documentSha256;
    private String reuseKey;
    private SchemaDraftEvaluationOutcomeStatus status;
    private boolean reused;
    private String reusedFromOutcomeId;
    private int chunkCount;
    private String metricsJson;
    private String evidenceCoordinatesJson;
    private String failureCategory;
    private boolean retryable;
    private Instant startedAt;
    private Instant completedAt;
}
