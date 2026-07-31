package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
public class SchemaDraftWorkflowCheckpointService {
    private final SchemaDraftEvaluationRunRepository evaluationRunRepository;
    private final SchemaDraftEvaluationOutcomeRepository evaluationOutcomeRepository;
    private final SchemaDraftPublicationRepository publicationRepository;
    private final SchemaDraftRepository draftRepository;
    private final SchemaReprocessingPlanRepository planRepository;
    private final SchemaReprocessingItemRepository itemRepository;

    public SchemaDraftWorkflowCheckpointService(
        SchemaDraftEvaluationRunRepository evaluationRunRepository,
        SchemaDraftEvaluationOutcomeRepository evaluationOutcomeRepository,
        SchemaDraftPublicationRepository publicationRepository,
        SchemaDraftRepository draftRepository,
        SchemaReprocessingPlanRepository planRepository,
        SchemaReprocessingItemRepository itemRepository
    ) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationOutcomeRepository = evaluationOutcomeRepository;
        this.publicationRepository = publicationRepository;
        this.draftRepository = draftRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
    }

    @RelationalTransactional
    public SchemaDraftEvaluationRunNode createEvaluation(
        SchemaDraftEvaluationRunNode run, List<SchemaDraftEvaluationOutcomeNode> outcomes
    ) {
        SchemaDraftEvaluationRunNode saved = evaluationRunRepository.save(run);
        outcomes.forEach(evaluationOutcomeRepository::save);
        return saved;
    }

    @RelationalTransactional
    public SchemaDraftPublicationNode savePublicationIntent(SchemaDraftPublicationNode publication) {
        return publicationRepository.save(publication);
    }

    @RelationalTransactional
    public SchemaDraftPublicationNode completePublication(
        SchemaDraftPublicationNode publication, SchemaDraftNode draft
    ) {
        SchemaDraftPublicationNode saved = publicationRepository.save(publication);
        draftRepository.save(draft);
        return saved;
    }

    @RelationalTransactional
    public SchemaReprocessingPlanNode createPlan(
        SchemaReprocessingPlanNode plan, List<SchemaReprocessingItemNode> items
    ) {
        if (planRepository.existsActiveByKnowledgeBaseId(plan.getKnowledgeBaseId())) {
            throw new ConflictException(
                "Another destructive reprocessing plan is active for knowledge base: "
                    + plan.getKnowledgeBaseId()
            );
        }
        try {
            SchemaReprocessingPlanNode saved = planRepository.save(plan);
            items.forEach(itemRepository::save);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                "Another destructive reprocessing plan is active for knowledge base: "
                    + plan.getKnowledgeBaseId()
            );
        }
    }

    @RelationalTransactional
    public SchemaReprocessingPlanNode repairPlan(
        SchemaReprocessingPlanNode plan, List<SchemaReprocessingItemNode> items
    ) {
        items.forEach(itemRepository::save);
        return planRepository.save(plan);
    }
}
