package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingProcessingOutcomeReader;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class SchemaReprocessingRecoveryService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(
        SchemaReprocessingRecoveryService.class
    );
    private final SchemaReprocessingPlanRepository planRepository;
    private final SchemaReprocessingItemRepository itemRepository;
    private final ReprocessingProcessingOutcomeReader processingOutcomes;
    private final SchemaDraftWorkflowCheckpointService checkpointService;
    private final SchemaDraftJsonSupport jsonSupport;

    public SchemaReprocessingRecoveryService(
        SchemaReprocessingPlanRepository planRepository,
        SchemaReprocessingItemRepository itemRepository,
        ReprocessingProcessingOutcomeReader processingOutcomes,
        SchemaDraftWorkflowCheckpointService checkpointService,
        SchemaDraftJsonSupport jsonSupport
    ) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.processingOutcomes = processingOutcomes;
        this.checkpointService = checkpointService;
        this.jsonSupport = jsonSupport;
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
            try {
                recoverPlan(plan, now);
            } catch (DataIntegrityViolationException | IllegalStateException exception) {
                log.warn(
                    "Schema reprocessing plan recovery skipped malformed plan: "
                        + "planId={}, exceptionType={}",
                    plan.getId(),
                    exception.getClass().getSimpleName()
                );
            }
        }
    }

    private void recoverPlan(SchemaReprocessingPlanNode plan, Instant now) {
        if (plan.getStatus() == SchemaReprocessingPlanStatus.RUNNING
            && (plan.getClaimUntil() == null || !plan.getClaimUntil().isBefore(now))) {
            return;
        }
        List<SchemaReprocessingItemNode> items =
            itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId());
        for (SchemaReprocessingItemNode item : items) {
            if (item.getStatus() == SchemaReprocessingItemStatus.QUEUED
                || item.getStatus() == SchemaReprocessingItemStatus.RUNNING
                    && item.getClaimUntil() != null && item.getClaimUntil().isBefore(now)) {
                recoverItem(plan, item, now);
            }
        }
        items = itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId());
        repairCounters(plan, items, now);
        checkpointService.repairPlan(plan, List.of());
    }

    private void recoverItem(
        SchemaReprocessingPlanNode plan,
        SchemaReprocessingItemNode item,
        Instant now
    ) {
        if (item.getStatus() == SchemaReprocessingItemStatus.RUNNING) {
            SchemaReprocessingItemStatus recovered = completedOverwrite(plan, item)
                ? SchemaReprocessingItemStatus.SUCCEEDED
                : SchemaReprocessingItemStatus.INTERRUPTED;
            itemRepository.complete(
                item.getId(),
                item.getClaimedBy(),
                recovered,
                recovered == SchemaReprocessingItemStatus.SUCCEEDED ? null : "CLAIM_EXPIRED",
                recovered != SchemaReprocessingItemStatus.SUCCEEDED,
                now
            );
            return;
        }
        item.setStatus(SchemaReprocessingItemStatus.INTERRUPTED);
        item.setFailureCategory("APPLICATION_RESTART");
        item.setRetryable(true);
        item.setCompletedAt(now);
        itemRepository.save(item);
    }

    public SchemaReprocessingPlanNode repairCounters(String planId) {
        SchemaReprocessingPlanNode plan = planRepository.findById(planId).orElseThrow();
        List<SchemaReprocessingItemNode> items =
            itemRepository.findByPlanIdOrderByDocumentIdAsc(planId);
        repairCounters(plan, items, Instant.now());
        return checkpointService.repairPlan(plan, List.of());
    }

    private boolean completedOverwrite(
        SchemaReprocessingPlanNode plan,
        SchemaReprocessingItemNode item
    ) {
        String expectedChunkerRevision = null;
        if (plan.getReason() == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION) {
            ChunkMigrationSnapshot snapshot = jsonSupport.read(
                plan.getTargetSnapshotJson(),
                ChunkMigrationSnapshot.class
            );
            ChunkMigrationSnapshot.DocumentTarget target = snapshot.documents().get(item.getDocumentId());
            if (target == null) {
                return false;
            }
            expectedChunkerRevision = target.effectiveChunkerRevision();
        }
        return processingOutcomes.completedOverwrite(new ReprocessingProcessingOutcomeReader.Request(
            item.getDocumentId(), item.getDocumentSha256(), expectedChunkerRevision, item.getStartedAt()));
    }

    private void repairCounters(
        SchemaReprocessingPlanNode plan, List<SchemaReprocessingItemNode> items, Instant completedAt
    ) {
        if (plan.getTotalDocuments() != items.size()) {
            log.warn(
                "Schema reprocessing plan item cardinality mismatch repaired: "
                    + "planId={}, storedTotalDocuments={}, itemCount={}",
                plan.getId(),
                plan.getTotalDocuments(),
                items.size()
            );
        }
        plan.setTotalDocuments(items.size());
        plan.setQueuedDocuments(count(items, SchemaReprocessingItemStatus.QUEUED));
        plan.setRunningDocuments(count(items, SchemaReprocessingItemStatus.RUNNING));
        plan.setSucceededDocuments(count(items, SchemaReprocessingItemStatus.SUCCEEDED));
        plan.setFailedDocuments(count(items, SchemaReprocessingItemStatus.FAILED)
            + count(items, SchemaReprocessingItemStatus.INTERRUPTED));
        plan.setStaleDocuments(count(items, SchemaReprocessingItemStatus.STALE_SOURCE));
        plan.setBlockedDocuments(count(items, SchemaReprocessingItemStatus.BLOCKED)
            + count(items, SchemaReprocessingItemStatus.BLOCKED_TARGET_CHANGED)
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
