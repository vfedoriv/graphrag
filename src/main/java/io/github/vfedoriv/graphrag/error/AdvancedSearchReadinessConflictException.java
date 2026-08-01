package io.github.vfedoriv.graphrag.error;

import io.github.vfedoriv.graphrag.dto.AdvancedSearchReadinessDtos.ReadinessIssue;
import java.util.List;

public class AdvancedSearchReadinessConflictException extends RuntimeException {
    public AdvancedSearchReadinessConflictException(List<ReadinessIssue> blockers) {
        super("Advanced search is not ready for admission");
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    private final List<ReadinessIssue> blockers;

    public List<ReadinessIssue> getBlockers() {
        return blockers;
    }
}
