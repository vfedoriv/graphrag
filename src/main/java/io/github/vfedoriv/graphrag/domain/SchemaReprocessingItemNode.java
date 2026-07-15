package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaReprocessingItem")
@Getter
@Setter
public class SchemaReprocessingItemNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String planId;
    private String documentId;
    private String documentSha256;
    private SchemaReprocessingItemStatus status;
    private String failureCategory;
    private boolean retryable;
    private String priorItemId;
    private Instant startedAt;
    private Instant completedAt;
}
