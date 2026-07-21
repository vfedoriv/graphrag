package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraftAggregateRevision")
@Getter
@Setter
public class SchemaDraftAggregateRevisionNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String draftId;
    private String runId;
    private long revision;
    private String candidatesJson;
    private String conflictsJson;
    private String warningsJson;
    private String schemaJson;
    private String contentHash;
    private DiffBaselineType diffBaselineType;
    private String diffBaselineId;
    private String diffBaselineSchemaJson;
    private String diffBaselineContentHash;
    private boolean partial;
    private Instant createdAt;
}
