package io.github.vfedoriv.graphrag.schemas.evaluation.application;
import io.github.vfedoriv.graphrag.schemas.evaluation.contracts.PublicationEvaluationQualifications;
import io.github.vfedoriv.graphrag.schemas.evaluation.configuration.SchemaDraftEvaluationProperties;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.util.Optional;
import org.springframework.stereotype.Service;
@Service
public class PublicationEvaluationQualificationsFacade implements PublicationEvaluationQualifications {
    private final SchemaDraftEvaluationRunRepository runs;
    private final SchemaDraftEvaluationProperties properties;
    public PublicationEvaluationQualificationsFacade(SchemaDraftEvaluationRunRepository runs, SchemaDraftEvaluationProperties properties) {
        this.runs = runs;
        this.properties = properties;
    }
    @Override public Requirement requirement() {
        return new Requirement(properties.requiredForPublication(), properties.minimumSuccessfulDocuments());
    }
    @Override @RelationalTransactional(readOnly = true)
    public Optional<Run> qualifying(String draftId, long revision, String projectionHash) {
        return runs.findByDraftIdOrderByCreatedAtDesc(draftId).stream()
            .filter(value -> value.getDraftRevision() == revision && projectionHash.equals(value.getProjectionContentHash()))
            .filter(value -> value.getStatus() == SchemaDraftEvaluationStatus.COMPLETED || value.getStatus() == SchemaDraftEvaluationStatus.PARTIAL)
            .findFirst().map(value -> new Run(value.getId(), value.getDraftRevision(), value.getProjectionContentHash(), value.getSucceededDocuments()));
    }
}
