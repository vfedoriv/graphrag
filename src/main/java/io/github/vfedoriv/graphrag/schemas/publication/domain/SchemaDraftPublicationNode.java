package io.github.vfedoriv.graphrag.schemas.publication.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftPublicationNode {
    private String id;
    private Long persistenceVersion;
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
