package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class SchemaReprocessingRecoveryService implements ApplicationRunner {
    private final SchemaReprocessingPlanRepository planRepository;
    private final SchemaReprocessingItemRepository itemRepository;
    private final DocumentProcessingRunRepository processingRunRepository;
    private final SchemaDraftWorkflowCheckpointService checkpointService;

    public SchemaReprocessingRecoveryService(
        SchemaReprocessingPlanRepository planRepository,
        SchemaReprocessingItemRepository itemRepository,
        DocumentProcessingRunRepository processingRunRepository,
        SchemaDraftWorkflowCheckpointService checkpointService
    ) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.processingRunRepository = processingRunRepository;
        this.checkpointService = checkpointService;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    @Scheduled(fixedDelay = 300_000L)
    public void recover() {
        Instant now = Instant.now();
        List<SchemaReprocessingPlanNode> interrupted = planRepository.findByStatusIn(
            List.of(SchemaReprocessingPlanStatus.QUEUED, SchemaReprocessingPlanStatus.RUNNING));
        for (SchemaReprocessingPlanNode plan : interrupted) {
            if (plan.getStatus() == SchemaReprocessingPlanStatus.RUNNING
                && (plan.getClaimUntil() == null || !plan.getClaimUntil().isBefore(now))) {
                continue;
            }
            List<SchemaReprocessingItemNode> items = itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId());
            for (SchemaReprocessingItemNode item : items) {
                if (item.getStatus() == SchemaReprocessingItemStatus.QUEUED
                    || item.getStatus() == SchemaReprocessingItemStatus.RUNNING
                        && item.getClaimUntil() != null && item.getClaimUntil().isBefore(now)) {
                    if (item.getStatus() == SchemaReprocessingItemStatus.RUNNING) {
                        SchemaReprocessingItemStatus recovered = completedOverwrite(item)
                            ? SchemaReprocessingItemStatus.SUCCEEDED
                            : SchemaReprocessingItemStatus.INTERRUPTED;
                        if (!Long.valueOf(1).equals(itemRepository.complete(
                            item.getId(), item.getClaimedBy(), recovered,
                            recovered == SchemaReprocessingItemStatus.SUCCEEDED
                                ? null : "CLAIM_EXPIRED",
                            recovered != SchemaReprocessingItemStatus.SUCCEEDED, now))) {
                            continue;
                        }
                    } else {
                        item.setStatus(SchemaReprocessingItemStatus.INTERRUPTED);
                        item.setFailureCategory("APPLICATION_RESTART");
                        item.setRetryable(true);
                        item.setCompletedAt(now);
                        itemRepository.save(item);
                    }
                }
            }
            items = itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId());
            repairCounters(plan, items, now);
            checkpointService.repairPlan(plan, List.of());
        }
    }

    public SchemaReprocessingPlanNode repairCounters(String planId) {
        SchemaReprocessingPlanNode plan = planRepository.findById(planId).orElseThrow();
        List<SchemaReprocessingItemNode> items =
            itemRepository.findByPlanIdOrderByDocumentIdAsc(planId);
        repairCounters(plan, items, Instant.now());
        return checkpointService.repairPlan(plan, List.of());
    }

    private boolean completedOverwrite(SchemaReprocessingItemNode item) {
        return processingRunRepository.findByDocumentIdOrderByStartedAtAsc(item.getDocumentId()).stream()
            .filter(run -> run.getStatus() == DocumentProcessingRunStatus.COMPLETED)
            .filter(DocumentProcessingRunNode::isActiveCompleted)
            .filter(run -> item.getDocumentSha256().equals(run.getSourceSha256()))
            .anyMatch(run -> item.getStartedAt() == null || !run.getStartedAt().isBefore(item.getStartedAt()));
    }

    private void repairCounters(
        SchemaReprocessingPlanNode plan, List<SchemaReprocessingItemNode> items, Instant completedAt
    ) {
        plan.setQueuedDocuments(count(items, SchemaReprocessingItemStatus.QUEUED));
        plan.setRunningDocuments(count(items, SchemaReprocessingItemStatus.RUNNING));
        plan.setSucceededDocuments(count(items, SchemaReprocessingItemStatus.SUCCEEDED));
        plan.setFailedDocuments(count(items, SchemaReprocessingItemStatus.FAILED)
            + count(items, SchemaReprocessingItemStatus.INTERRUPTED));
        plan.setStaleDocuments(count(items, SchemaReprocessingItemStatus.STALE_SOURCE));
        plan.setBlockedDocuments(count(items, SchemaReprocessingItemStatus.BLOCKED)
            + count(items, SchemaReprocessingItemStatus.SKIPPED));
        if (plan.getQueuedDocuments() == 0 && plan.getRunningDocuments() == 0) {
            plan.setStatus(plan.getSucceededDocuments() == items.size()
                ? SchemaReprocessingPlanStatus.COMPLETED
                : plan.getSucceededDocuments() > 0
                    ? SchemaReprocessingPlanStatus.PARTIAL : SchemaReprocessingPlanStatus.INTERRUPTED);
            plan.setCompletedAt(completedAt);
        }
    }

    private int count(List<SchemaReprocessingItemNode> items, SchemaReprocessingItemStatus status) {
        return (int) items.stream().filter(item -> item.getStatus() == status).count();
    }
}
