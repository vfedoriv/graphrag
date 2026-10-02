package io.github.vfedoriv.graphrag.search.runs.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvancedSearchAttemptNode {
    private String id;
    private String runId;
    private int roundNumber;
    private String subqueryId;
    private String retriever;
    private String status;
    private int candidateCount;
    private long latencyMs;
    private String failureCategory;
    private Instant createdAt;
    private Instant completedAt;
    private Long persistenceVersion;
}
