package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftEvaluationOutcomeNode {
    private String id;
    private Long persistenceVersion;
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
