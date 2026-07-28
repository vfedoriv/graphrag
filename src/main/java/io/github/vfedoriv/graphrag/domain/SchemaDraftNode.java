package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftNode {
    private String id;
    private Long persistenceVersion;
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
