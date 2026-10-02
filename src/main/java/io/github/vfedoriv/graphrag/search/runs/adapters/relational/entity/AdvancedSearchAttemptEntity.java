package io.github.vfedoriv.graphrag.search.runs.adapters.relational.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "advanced_search_attempt")
@Getter
@Setter
public class AdvancedSearchAttemptEntity {
    @Id private String id;
    @Column(name = "run_id", nullable = false) private String runId;
    @Column(name = "round_number", nullable = false) private int roundNumber;
    @Column(name = "subquery_id") private String subqueryId;
    @Column(nullable = false, length = 32) private String retriever;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "candidate_count", nullable = false) private int candidateCount;
    @Column(name = "latency_ms", nullable = false) private long latencyMs;
    @Column(name = "failure_category", length = 80) private String failureCategory;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version @Column(name = "version") private Long persistenceVersion;
}
