package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftSourceResult")
@Getter
@Setter
public class SchemaDraftSourceResultNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String draftId;
    private String runId;
    private String sourceId;
    private long sourceRevision;
    private String sourceSha256;
    private String reuseKey;
    private SchemaDraftSourceResultStatus status;
    private boolean reused;
    private String candidatesJson;
    private String aliasesJson;
    private String failureCategory;
    private boolean retryable;
    private int chunkCount;
    private Instant createdAt;
    private Instant completedAt;
}
