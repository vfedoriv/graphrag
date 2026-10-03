package io.github.vfedoriv.graphrag.schemas.evaluation.application;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationCheckpointServiceTest {
    private final SchemaDraftEvaluationRunRepository runs = mock(SchemaDraftEvaluationRunRepository.class);
    private final SchemaDraftEvaluationOutcomeRepository outcomes = mock(SchemaDraftEvaluationOutcomeRepository.class);
    private final EvaluationCheckpointService checkpoints = new EvaluationCheckpointService(runs, outcomes);
    private final Instant now = Instant.parse("2026-07-21T08:15:30Z");

    @Test void savesCreationBeforeOutcomeCheckpoints() {
        SchemaDraftEvaluationRunNode run = run(SchemaDraftEvaluationStatus.QUEUED);
        SchemaDraftEvaluationOutcomeNode outcome = outcome(SchemaDraftEvaluationOutcomeStatus.QUEUED);
        when(runs.save(run)).thenReturn(run);
        assertThat(checkpoints.create(run, List.of(outcome))).isSameAs(run);
        org.mockito.InOrder order = inOrder(runs, outcomes);
        order.verify(runs).save(run);
        order.verify(outcomes).save(outcome);
    }
    @Test void newerOwnerPreventsOutcomeRecovery() {
        SchemaDraftEvaluationRunNode run = run(SchemaDraftEvaluationStatus.RUNNING);
        run.setClaimUntil(now.minusSeconds(1));
        run.setClaimedBy("old-worker");
        when(runs.interruptExpired("run", "old-worker", now, now)).thenReturn(0L);
        checkpoints.recover(run, now);
        verifyNoInteractions(outcomes);
        verify(runs, never()).save(any());
    }
    @Test void nullOrLiveClaimPreservesExistingRecoveryPredicate() {
        SchemaDraftEvaluationRunNode run = run(SchemaDraftEvaluationStatus.RUNNING);
        checkpoints.recover(run, now);
        run.setClaimUntil(now);
        checkpoints.recover(run, now);
        run.setClaimUntil(now.plusSeconds(1));
        checkpoints.recover(run, now);
        verifyNoInteractions(runs, outcomes);
    }
    @Test void expiredClaimInterruptsPendingOutcomesWithoutOverwritingClaimedRun() {
        SchemaDraftEvaluationRunNode run = run(SchemaDraftEvaluationStatus.RUNNING);
        run.setClaimUntil(now.minusSeconds(1)); run.setClaimedBy("worker");
        SchemaDraftEvaluationOutcomeNode queued = outcome(SchemaDraftEvaluationOutcomeStatus.QUEUED);
        SchemaDraftEvaluationOutcomeNode done = outcome(SchemaDraftEvaluationOutcomeStatus.SUCCEEDED);
        when(runs.interruptExpired("run", "worker", now, now)).thenReturn(1L);
        when(outcomes.findByRunIdOrderByDocumentIdAsc("run")).thenReturn(List.of(queued, done));
        checkpoints.recover(run, now);
        assertThat(queued.getStatus()).isEqualTo(SchemaDraftEvaluationOutcomeStatus.INTERRUPTED);
        assertThat(queued.getFailureCategory()).isEqualTo("APPLICATION_RESTART");
        assertThat(queued.getCompletedAt()).isEqualTo(now);
        assertThat(queued.isRetryable()).isTrue();
        assertThat(done.getStatus()).isEqualTo(SchemaDraftEvaluationOutcomeStatus.SUCCEEDED);
        verify(outcomes, never()).save(done);
        verify(runs, never()).save(any());
    }
    @Test void queuedRunAndRunningOutcomeAreMarkedInterrupted() {
        SchemaDraftEvaluationRunNode run = run(SchemaDraftEvaluationStatus.QUEUED);
        SchemaDraftEvaluationOutcomeNode outcome = outcome(SchemaDraftEvaluationOutcomeStatus.RUNNING);
        when(outcomes.findByRunIdOrderByDocumentIdAsc("run")).thenReturn(List.of(outcome));
        checkpoints.recover(run, now);
        assertThat(run.getStatus()).isEqualTo(SchemaDraftEvaluationStatus.INTERRUPTED);
        assertThat(outcome.getStatus()).isEqualTo(SchemaDraftEvaluationOutcomeStatus.INTERRUPTED);
        assertThat(run.isRetryable()).isTrue();
        verify(runs).save(run);
    }
    private SchemaDraftEvaluationRunNode run(SchemaDraftEvaluationStatus status) {
        SchemaDraftEvaluationRunNode run = new SchemaDraftEvaluationRunNode(); run.setId("run"); run.setStatus(status); return run;
    }
    private SchemaDraftEvaluationOutcomeNode outcome(SchemaDraftEvaluationOutcomeStatus status) {
        SchemaDraftEvaluationOutcomeNode outcome = new SchemaDraftEvaluationOutcomeNode(); outcome.setStatus(status); return outcome;
    }
}
