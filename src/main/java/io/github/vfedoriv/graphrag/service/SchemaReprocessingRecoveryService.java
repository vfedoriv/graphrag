package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SchemaReprocessingRecoveryService implements ApplicationRunner {
    private final SchemaReprocessingPlanRepository planRepository;
    private final SchemaReprocessingItemRepository itemRepository;

    public SchemaReprocessingRecoveryService(
        SchemaReprocessingPlanRepository planRepository, SchemaReprocessingItemRepository itemRepository
    ) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SchemaReprocessingPlanNode> interrupted = planRepository.findByStatusIn(
            List.of(SchemaReprocessingPlanStatus.QUEUED, SchemaReprocessingPlanStatus.RUNNING));
        for (SchemaReprocessingPlanNode plan : interrupted) {
            for (SchemaReprocessingItemNode item : itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId())) {
                if (item.getStatus() == SchemaReprocessingItemStatus.QUEUED
                    || item.getStatus() == SchemaReprocessingItemStatus.RUNNING) {
                    item.setStatus(SchemaReprocessingItemStatus.INTERRUPTED);
                    item.setFailureCategory("APPLICATION_RESTART");
                    item.setRetryable(true);
                    item.setCompletedAt(Instant.now());
                    itemRepository.save(item);
                }
            }
            plan.setStatus(SchemaReprocessingPlanStatus.INTERRUPTED);
            plan.setCompletedAt(Instant.now());
            planRepository.save(plan);
        }
    }
}
