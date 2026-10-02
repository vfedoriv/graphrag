package io.github.vfedoriv.graphrag.schemas.drafts.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftSourceRevisionNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String sourceId;
    private long revision;
    private SchemaDraftSourceStatus status;
    private String sha256;
    private String documentId;
    private String contentUri;
    private Instant createdAt;
}
