package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.application.ReprocessingCheckpointService;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingPlanRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReprocessingCheckpointLegacyBehaviorTest {

    @Test
    void rejectsAChunkPlanWhileAnyDestructivePlanIsActive() {
        SchemaReprocessingPlanRepository plans = mock(SchemaReprocessingPlanRepository.class);
        SchemaReprocessingItemRepository items = mock(SchemaReprocessingItemRepository.class);
        when(plans.existsActiveByKnowledgeBaseId("kb-1")).thenReturn(true);
        ReprocessingCheckpointService service = new ReprocessingCheckpointService(plans, items);
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setKnowledgeBaseId("kb-1");
        plan.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);

        assertThatThrownBy(() -> service.createPlan(plan, List.of()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Another destructive reprocessing plan");
        verify(plans, never()).save(plan);
    }
}
