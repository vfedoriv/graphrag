package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class SchemaDraftEvaluationRecoveryService implements ApplicationRunner {
    private final SchemaDraftEvaluationRunRepository runRepository;
    private final SchemaDraftEvaluationOutcomeRepository outcomeRepository;

    public SchemaDraftEvaluationRecoveryService(
        SchemaDraftEvaluationRunRepository runRepository,
        SchemaDraftEvaluationOutcomeRepository outcomeRepository
    ) {
        this.runRepository = runRepository;
        this.outcomeRepository = outcomeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    @Scheduled(fixedDelay = 300_000L)
    public void recover() {
        Instant now = Instant.now();
        List<SchemaDraftEvaluationRunNode> interrupted = runRepository.findByStatusIn(
            List.of(SchemaDraftEvaluationStatus.QUEUED, SchemaDraftEvaluationStatus.RUNNING));
        for (SchemaDraftEvaluationRunNode run : interrupted) {
            if (run.getStatus() == SchemaDraftEvaluationStatus.RUNNING
                && (run.getClaimUntil() == null || !run.getClaimUntil().isBefore(now)
                    || !Long.valueOf(1).equals(runRepository.interruptExpired(
                        run.getId(), run.getClaimedBy(), now, now)))) {
                continue;
            }
            for (SchemaDraftEvaluationOutcomeNode outcome : outcomeRepository.findByRunIdOrderByDocumentIdAsc(run.getId())) {
                if (outcome.getStatus() == SchemaDraftEvaluationOutcomeStatus.QUEUED
                    || outcome.getStatus() == SchemaDraftEvaluationOutcomeStatus.RUNNING) {
                    outcome.setStatus(SchemaDraftEvaluationOutcomeStatus.INTERRUPTED);
                    outcome.setFailureCategory("APPLICATION_RESTART");
                    outcome.setRetryable(true);
                    outcome.setCompletedAt(now);
                    outcomeRepository.save(outcome);
                }
            }
            if (run.getStatus() == SchemaDraftEvaluationStatus.QUEUED) {
                run.setStatus(SchemaDraftEvaluationStatus.INTERRUPTED);
                run.setFailureCategory("APPLICATION_RESTART");
                run.setRetryable(true);
                run.setCompletedAt(now);
                runRepository.save(run);
            }
        }
    }
}
