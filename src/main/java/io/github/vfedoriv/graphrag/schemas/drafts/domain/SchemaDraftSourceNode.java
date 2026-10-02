package io.github.vfedoriv.graphrag.schemas.drafts.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftSourceNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String knowledgeBaseId;
    private SchemaDraftSourceType type;
    private SchemaDraftSourceStatus status;
    private long revision;
    private String documentId;
    private String name;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private String contentUri;
    private boolean analyzed;
    private Instant createdAt;
    private Instant updatedAt;
}
