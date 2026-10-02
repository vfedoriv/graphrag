package io.github.vfedoriv.graphrag.search.runs.domain;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunNode;
import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStatus;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class AdvancedSearchRunLifecycle {
    public Duration remaining(AdvancedSearchRunNode run, Instant now) {
        Duration remaining = Duration.between(now, run.getDeadlineAt());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean shouldStop(AdvancedSearchRunNode run, Instant now) {
        return run.getStatus().terminal() || run.getCancellationRequestedAt() != null
            || !now.isBefore(run.getDeadlineAt()) || Thread.currentThread().isInterrupted();
    }

    public void moveTo(AdvancedSearchRunNode run, AdvancedSearchRunStage stage) {
        if (run.getStatus() != AdvancedSearchRunStatus.RUNNING || stage == AdvancedSearchRunStage.QUEUED) {
            throw new IllegalStateException("Illegal advanced-search stage transition");
        }
        run.setStage(stage);
    }

    public void terminal(
        AdvancedSearchRunNode run,
        AdvancedSearchRunStatus status,
        String failureCategory,
        int evidenceCount,
        Instant now,
        Duration retention
    ) {
        if (!status.terminal()) {
            throw new IllegalArgumentException("Target status must be terminal");
        }
        if (run.getStatus().terminal()) {
            if (run.getStatus() != status) {
                throw new IllegalStateException("Terminal advanced-search state is immutable");
            }
            return;
        }
        run.setStatus(status);
        run.setStage(AdvancedSearchRunStage.TERMINAL);
        run.setFailureCategory(sanitize(failureCategory));
        run.setEvidenceCount(Math.max(0, evidenceCount));
        run.setCompletedAt(now);
        run.setExpiresAt(now.plus(retention));
    }

    private String sanitize(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String sanitized = category.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(80, sanitized.length()));
    }
}
