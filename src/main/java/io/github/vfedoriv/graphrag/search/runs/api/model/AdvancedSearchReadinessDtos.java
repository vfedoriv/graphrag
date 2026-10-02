package io.github.vfedoriv.graphrag.search.runs.api.model;

import java.util.List;

public final class AdvancedSearchReadinessDtos {
    private AdvancedSearchReadinessDtos() { }

    public record ReadinessResponse(
        String knowledgeBaseId,
        boolean ready,
        String profileId,
        long profileRevision,
        boolean graphBranchAvailable,
        boolean embeddedCorpusPresent,
        List<ReadinessIssue> blockers,
        List<ReadinessIssue> informational
    ) {
        public ReadinessResponse {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            informational = informational == null ? List.of() : List.copyOf(informational);
        }
    }

    public record ReadinessIssue(String code, String description) { }
}
