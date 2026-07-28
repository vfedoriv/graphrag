package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftStorageMutationNode {
    private String id;
    private Long persistenceVersion;
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
