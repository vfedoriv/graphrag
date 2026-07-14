package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftSourceRevision")
@Getter
@Setter
public class SchemaDraftSourceRevisionNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String draftId;
    private String sourceId;
    private long revision;
    private SchemaDraftSourceStatus status;
    private String sha256;
    private String documentId;
    private String contentUri;
    private Instant createdAt;
}
