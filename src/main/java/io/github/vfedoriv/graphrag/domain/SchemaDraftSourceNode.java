package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftSource")
@Getter
@Setter
public class SchemaDraftSourceNode {
    @Id private String id;
    @Version private Long persistenceVersion;
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
