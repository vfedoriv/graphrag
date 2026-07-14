package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftStorageMutation")
@Getter
@Setter
public class SchemaDraftStorageMutationNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private SchemaDraftStorageMutationType type;
    private SchemaDraftStorageMutationState state;
    private String draftId;
    private String sourceId;
    private String contentUri;
    private int retryCount;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
