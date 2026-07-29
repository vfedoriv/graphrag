package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftPublication")
@Getter
@Setter
public class SchemaDraftPublicationNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String draftId;
    private String knowledgeBaseId;
    private String targetIdentity;
    private long draftRevision;
    private String aggregateRevisionId;
    private String projectionContentHash;
    private SchemaDraftPublicationStatus status;
    private String schemaId;
    private int retryCount;
    private String failureCategory;
    private boolean retryable;
    private Instant createdAt;
    private Instant completedAt;
}
