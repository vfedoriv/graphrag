package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdvancedSearchRunLifecycleTest {
    private final AdvancedSearchRunLifecycle lifecycle = new AdvancedSearchRunLifecycle();

    @Test
    void computesBoundedRemainingTimeAndRecognizesCancellationAndDeadlines() {
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        AdvancedSearchRunNode run = running(now.plusSeconds(5));
        assertThat(lifecycle.remaining(run, now)).isEqualTo(Duration.ofSeconds(5));
        assertThat(lifecycle.remaining(run, now.plusSeconds(6))).isZero();
        assertThat(lifecycle.shouldStop(run, now)).isFalse();
        run.setCancellationRequestedAt(now);
        assertThat(lifecycle.shouldStop(run, now)).isTrue();
    }

    @Test
    void terminalStateIsImmutableAndSanitizesFailureCategory() {
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        AdvancedSearchRunNode run = running(now.plusSeconds(5));
        lifecycle.terminal(run, AdvancedSearchRunStatus.FAILED, "provider secret response", 0,
            now, Duration.ofHours(24));
        assertThat(run.getStatus()).isEqualTo(AdvancedSearchRunStatus.FAILED);
        assertThat(run.getStage()).isEqualTo(AdvancedSearchRunStage.TERMINAL);
        assertThat(run.getFailureCategory()).isEqualTo("provider_secret_response");
        assertThat(run.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
        assertThatThrownBy(() -> lifecycle.terminal(run, AdvancedSearchRunStatus.COMPLETED, null, 1,
            now, Duration.ofHours(24))).isInstanceOf(IllegalStateException.class);
    }

    private AdvancedSearchRunNode running(Instant deadline) {
        AdvancedSearchRunNode run = new AdvancedSearchRunNode();
        run.setStatus(AdvancedSearchRunStatus.RUNNING); run.setStage(AdvancedSearchRunStage.RETRIEVAL);
        run.setDeadlineAt(deadline); return run;
    }
}
