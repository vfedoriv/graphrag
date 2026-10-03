package io.github.vfedoriv.graphrag.search.runs.adapters.relational.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "advanced_search_result")
@Getter
@Setter
public class AdvancedSearchResultEntity {
    @Id @Column(name = "run_id") private String runId;
    @Column(name = "payload_version", nullable = false) private int payloadVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb") private String resultJson;
    @Column(name = "evidence_count", nullable = false) private int evidenceCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version @Column(name = "version") private Long persistenceVersion;
}
