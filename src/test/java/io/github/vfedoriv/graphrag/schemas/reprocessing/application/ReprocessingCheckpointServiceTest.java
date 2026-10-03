package io.github.vfedoriv.graphrag.schemas.reprocessing.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.*;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ReprocessingCheckpointServiceTest {
    private final SchemaReprocessingPlanRepository plans = mock(SchemaReprocessingPlanRepository.class);
    private final SchemaReprocessingItemRepository items = mock(SchemaReprocessingItemRepository.class);
    private final ReprocessingCheckpointService service = new ReprocessingCheckpointService(plans, items);

    @Test
    void bothReasonsRejectAnExistingDestructivePlanBeforeSaving() {
        for (ReprocessingPlanReason reason : ReprocessingPlanReason.values()) {
            SchemaReprocessingPlanNode plan = plan(reason);
            when(plans.existsActiveByKnowledgeBaseId("kb")).thenReturn(true);
            assertThatThrownBy(() -> service.createPlan(plan, List.of()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Another destructive");
        }
        verify(plans, never()).save(any());
        verifyNoInteractions(items);
    }

    @Test
    void concurrentDatabaseExclusionKeepsTheConflictContract() {
        SchemaReprocessingPlanNode plan = plan(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        when(plans.save(plan)).thenThrow(new DataIntegrityViolationException("unique claim"));
        assertThatThrownBy(() -> service.createPlan(plan, List.of(new SchemaReprocessingItemNode())))
            .isInstanceOf(ConflictException.class).hasMessageContaining("knowledge base: kb");
        verifyNoInteractions(items);
    }

    @Test
    void repairPreservesItemBeforePlanCheckpointOrder() {
        SchemaReprocessingPlanNode plan = plan(ReprocessingPlanReason.SCHEMA_ACTIVATION);
        SchemaReprocessingItemNode item = new SchemaReprocessingItemNode();
        service.repairPlan(plan, List.of(item));
        org.mockito.InOrder order = inOrder(items, plans);
        order.verify(items).save(item);
        order.verify(plans).save(plan);
        verify(plans, never()).existsActiveByKnowledgeBaseId(any());
    }

    private SchemaReprocessingPlanNode plan(ReprocessingPlanReason reason) {
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setKnowledgeBaseId("kb");
        plan.setReason(reason);
        return plan;
    }
}
