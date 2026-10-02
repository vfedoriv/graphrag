package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftJsonSupport;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftWorkflowCheckpointService;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingProcessingOutcomeReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchemaReprocessingRecoveryServiceTest {
    private final SchemaReprocessingPlanRepository plans = mock(SchemaReprocessingPlanRepository.class);
    private final SchemaReprocessingItemRepository items = mock(SchemaReprocessingItemRepository.class);
    private final SchemaDraftWorkflowCheckpointService checkpoint = mock(SchemaDraftWorkflowCheckpointService.class);
    private final SchemaDraftJsonSupport json = new SchemaDraftJsonSupport(new ObjectMapper());

    @Test
    void reconcilesExternalSuccessAndMakesUnmatchedWorkRetryableForBothReasons() {
        for (ReprocessingPlanReason reason : ReprocessingPlanReason.values()) {
            for (boolean match : new boolean[] {true, false}) {
                SchemaReprocessingPlanNode plan = plan("plan", reason);
                SchemaReprocessingItemNode item = runningItem("item", plan.getId());
                when(plans.findByStatusIn(any())).thenReturn(List.of(plan));
                when(items.findByPlanIdOrderByDocumentIdAsc(plan.getId())).thenReturn(List.of(item));
                when(items.complete(eq(item.getId()), eq("worker"), any(), any(), anyBoolean(), any()))
                    .thenAnswer(call -> {
                        item.setStatus(call.getArgument(2));
                        item.setFailureCategory(call.getArgument(3));
                        item.setRetryable(call.getArgument(4));
                        return 1L;
                    });
                ReprocessingProcessingOutcomeReader outcomes = request -> {
                    assertThat(request.documentId()).isEqualTo("doc");
                    assertThat(request.expectedSourceSha256()).isEqualTo("hash");
                    assertThat(request.itemStartedAt()).isEqualTo(item.getStartedAt());
                    assertThat(request.requiredChunkerRevision()).isEqualTo(
                        reason == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION ? "effective" : null);
                    return match;
                };
                new SchemaReprocessingRecoveryService(plans, items, outcomes, checkpoint, json).recover();
                assertThat(item.getStatus()).isEqualTo(match
                    ? SchemaReprocessingItemStatus.SUCCEEDED : SchemaReprocessingItemStatus.INTERRUPTED);
                assertThat(item.isRetryable()).isEqualTo(!match);
                assertThat(item.getFailureCategory()).isEqualTo(match ? null : "CLAIM_EXPIRED");
                assertThat(plan.getTotalDocuments()).isEqualTo(1);
                assertThat(plan.getSucceededDocuments()).isEqualTo(match ? 1 : 0);
                assertThat(plan.getFailedDocuments()).isEqualTo(match ? 0 : 1);
                assertThat(plan.getRunningDocuments()).isZero();
                assertThat(plan.getStatus()).isEqualTo(match
                    ? SchemaReprocessingPlanStatus.COMPLETED : SchemaReprocessingPlanStatus.INTERRUPTED);
            }
        }
    }

    @Test
    void malformedPlanDoesNotPreventUnrelatedRecoveryAndCountersCanBeRepaired() {
        SchemaReprocessingPlanNode malformed = plan("bad", ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        malformed.setTargetSnapshotJson("{invalid");
        SchemaReprocessingPlanNode valid = plan("good", ReprocessingPlanReason.SCHEMA_ACTIVATION);
        SchemaReprocessingItemNode bad = runningItem("bad-item", malformed.getId());
        SchemaReprocessingItemNode queued = runningItem("good-item", valid.getId());
        queued.setStatus(SchemaReprocessingItemStatus.QUEUED);
        when(plans.findByStatusIn(any())).thenReturn(List.of(malformed, valid));
        when(items.findByPlanIdOrderByDocumentIdAsc(malformed.getId())).thenReturn(List.of(bad));
        when(items.findByPlanIdOrderByDocumentIdAsc(valid.getId())).thenReturn(List.of(queued));
        ReprocessingProcessingOutcomeReader outcomes = mock(ReprocessingProcessingOutcomeReader.class);
        SchemaReprocessingRecoveryService service = new SchemaReprocessingRecoveryService(plans, items, outcomes, checkpoint, json);
        service.recover();
        assertThat(bad.getStatus()).isEqualTo(SchemaReprocessingItemStatus.RUNNING);
        assertThat(queued.getStatus()).isEqualTo(SchemaReprocessingItemStatus.INTERRUPTED);
        assertThat(queued.getFailureCategory()).isEqualTo("APPLICATION_RESTART");
        assertThat(queued.isRetryable()).isTrue();
        assertThat(valid.getFailedDocuments()).isEqualTo(1);
        verifyNoInteractions(outcomes);
        when(plans.findById("good")).thenReturn(Optional.of(valid));
        when(checkpoint.repairPlan(eq(valid), any())).thenReturn(valid);
        queued.setStatus(SchemaReprocessingItemStatus.SUCCEEDED);
        assertThat(service.repairCounters("good").getSucceededDocuments()).isEqualTo(1);
        assertThat(valid.getFailedDocuments()).isZero();
        assertThat(valid.getStatus()).isEqualTo(SchemaReprocessingPlanStatus.COMPLETED);
    }

    @Test
    void migrationWithoutDocumentTargetCannotRecoverAsSucceeded() {
        SchemaReprocessingPlanNode plan = plan("plan", ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        plan.setTargetSnapshotJson(json.canonical(new ChunkMigrationSnapshot(
            "revision", ChunkReprocessingSelection.ALL, null, "profile", 3, "space", "schema", "schema-hash", Map.of())));
        SchemaReprocessingItemNode item = runningItem("item", "plan");
        when(plans.findByStatusIn(any())).thenReturn(List.of(plan));
        when(items.findByPlanIdOrderByDocumentIdAsc("plan")).thenReturn(List.of(item));
        ReprocessingProcessingOutcomeReader outcomes = mock(ReprocessingProcessingOutcomeReader.class);
        new SchemaReprocessingRecoveryService(plans, items, outcomes, checkpoint, json).recover();
        verify(items).complete(eq("item"), eq("worker"), eq(SchemaReprocessingItemStatus.INTERRUPTED),
            eq("CLAIM_EXPIRED"), eq(true), any());
        verifyNoInteractions(outcomes);
    }

    private SchemaReprocessingPlanNode plan(String id, ReprocessingPlanReason reason) {
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setId(id);
        plan.setReason(reason);
        plan.setStatus(SchemaReprocessingPlanStatus.RUNNING);
        plan.setClaimUntil(Instant.now().minusSeconds(60));
        plan.setTotalDocuments(99);
        plan.setRunningDocuments(99);
        plan.setTargetSnapshotJson(json.canonical(new ChunkMigrationSnapshot(
            "revision", ChunkReprocessingSelection.ALL, null, "profile", 3, "space", "schema", "schema-hash",
            Map.of("doc", new ChunkMigrationSnapshot.DocumentTarget("hash", "text", "text-v1", "TXT", "effective", Map.of())))));
        return plan;
    }

    private SchemaReprocessingItemNode runningItem(String id, String planId) {
        SchemaReprocessingItemNode item = new SchemaReprocessingItemNode();
        item.setId(id);
        item.setPlanId(planId);
        item.setDocumentId("doc");
        item.setDocumentSha256("hash");
        item.setStatus(SchemaReprocessingItemStatus.RUNNING);
        item.setClaimedBy("worker");
        item.setClaimUntil(Instant.now().minusSeconds(60));
        item.setStartedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return item;
    }
}
