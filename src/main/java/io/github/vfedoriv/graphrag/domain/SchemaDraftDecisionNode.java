package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftDecision")
@Getter
@Setter
public class SchemaDraftDecisionNode {
    @Id private String id;
    @Version private Long persistenceVersion;
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
