package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftConflictNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String aggregateRevisionId;
    private SchemaDraftConflictType type;
    private String coordinate;
    private String semanticKey;
    private String alternativesJson;
    private String evidenceJson;
    private boolean resolved;
    private String selectedAlternative;
    private String customResolutionJson;
    private Instant createdAt;
    private Instant resolvedAt;
}
