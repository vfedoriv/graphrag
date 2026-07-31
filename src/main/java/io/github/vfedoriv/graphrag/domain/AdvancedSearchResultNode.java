package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvancedSearchResultNode {
    private String runId;
    private int payloadVersion;
    private String resultJson;
    private int evidenceCount;
    private Instant createdAt;
    private Long persistenceVersion;
}
