package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftWorkflowCheckpointService;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaDraftWorkflowCheckpointServiceTest {

    @Test
    void rejectsAChunkPlanWhileAnyDestructivePlanIsActive() {
        SchemaReprocessingPlanRepository plans = mock(SchemaReprocessingPlanRepository.class);
        SchemaReprocessingItemRepository items = mock(SchemaReprocessingItemRepository.class);
        when(plans.existsActiveByKnowledgeBaseId("kb-1")).thenReturn(true);
        SchemaDraftWorkflowCheckpointService service = new SchemaDraftWorkflowCheckpointService(
            mock(SchemaDraftEvaluationRunRepository.class),
            mock(SchemaDraftEvaluationOutcomeRepository.class),
            mock(SchemaDraftPublicationRepository.class),
            mock(SchemaDraftRepository.class),
            plans,
            items
        );
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setKnowledgeBaseId("kb-1");
        plan.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);

        assertThatThrownBy(() -> service.createPlan(plan, List.of()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Another destructive reprocessing plan");
        verify(plans, never()).save(plan);
    }
}
