package io.github.vfedoriv.graphrag.schemas.evaluation.application;

import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationRunRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class SchemaDraftEvaluationRecoveryService implements ApplicationRunner {
    private final SchemaDraftEvaluationRunRepository runRepository;
    private final EvaluationCheckpointService checkpointService;

    public SchemaDraftEvaluationRecoveryService(
        SchemaDraftEvaluationRunRepository runRepository,
        EvaluationCheckpointService checkpointService
    ) {
        this.runRepository = runRepository;
        this.checkpointService = checkpointService;
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
            checkpointService.recover(run, now);
        }
    }
}
