package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("SchemaDraft")
@Getter
@Setter
public class SchemaDraftNode {
    @Id private String id;
    @Version private Long persistenceVersion;
    private String knowledgeBaseId;
    private String targetName;
    private int targetVersion;
    private String baseSchemaId;
    private SchemaDraftStatus status;
    private long revision;
    private long guidanceRevision;
    private String guidanceJson;
    private String guidanceFingerprint;
    private String currentAggregateId;
    private String runningAnalysisRunId;
    private String publicationSchemaId;
    private String publicationContentHash;
    private String activeAiProfileId;
    private long activeAiProfileRevision;
    private Instant createdAt;
    private Instant updatedAt;
}
