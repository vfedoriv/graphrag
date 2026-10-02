package io.github.vfedoriv.graphrag.schemas.evaluation.application;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.util.List;
import java.time.Instant;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeStatus;
import org.springframework.stereotype.Service;
@Service
public class EvaluationCheckpointService {
    private final SchemaDraftEvaluationRunRepository runs;
    private final SchemaDraftEvaluationOutcomeRepository outcomes;
    public EvaluationCheckpointService(SchemaDraftEvaluationRunRepository runs, SchemaDraftEvaluationOutcomeRepository outcomes) {
        this.runs = runs;
        this.outcomes = outcomes;
    }
    @RelationalTransactional
    public SchemaDraftEvaluationRunNode create(SchemaDraftEvaluationRunNode run, List<SchemaDraftEvaluationOutcomeNode> values) {
        SchemaDraftEvaluationRunNode saved = runs.save(run);
        values.forEach(outcomes::save);
        return saved;
    }
    @RelationalTransactional
    public void recover(SchemaDraftEvaluationRunNode run, Instant now) {
        if (run.getStatus() == SchemaDraftEvaluationStatus.RUNNING
            && (run.getClaimUntil() == null || !run.getClaimUntil().isBefore(now)
                || !Long.valueOf(1).equals(runs.interruptExpired(run.getId(), run.getClaimedBy(), now, now)))) return;
        for (SchemaDraftEvaluationOutcomeNode outcome : outcomes.findByRunIdOrderByDocumentIdAsc(run.getId())) {
            if (outcome.getStatus() == SchemaDraftEvaluationOutcomeStatus.QUEUED
                || outcome.getStatus() == SchemaDraftEvaluationOutcomeStatus.RUNNING) {
                outcome.setStatus(SchemaDraftEvaluationOutcomeStatus.INTERRUPTED);
                outcome.setFailureCategory("APPLICATION_RESTART");
                outcome.setRetryable(true);
                outcome.setCompletedAt(now);
                outcomes.save(outcome);
            }
        }
        if (run.getStatus() == SchemaDraftEvaluationStatus.QUEUED) {
            run.setStatus(SchemaDraftEvaluationStatus.INTERRUPTED);
            run.setFailureCategory("APPLICATION_RESTART");
            run.setRetryable(true);
            run.setCompletedAt(now);
            runs.save(run);
        }
    }
}
