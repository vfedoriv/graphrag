package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftDecisionNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private long sequence;
    private long draftRevision;
    private SchemaDraftDecisionType type;
    private SchemaDraftReviewState reviewState;
    private String candidateIdentity;
    private String priorValueJson;
    private String resultingValueJson;
    private String rationale;
    private Instant createdAt;
}
