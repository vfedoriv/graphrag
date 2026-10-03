package io.github.vfedoriv.graphrag.schemas.reprocessing.application;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ReprocessingCheckpointService {
    private final SchemaReprocessingPlanRepository planRepository;
    private final SchemaReprocessingItemRepository itemRepository;

    public ReprocessingCheckpointService(SchemaReprocessingPlanRepository planRepository,
                                        SchemaReprocessingItemRepository itemRepository) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
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
