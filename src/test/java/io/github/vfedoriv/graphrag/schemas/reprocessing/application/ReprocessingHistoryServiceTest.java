package io.github.vfedoriv.graphrag.schemas.reprocessing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.reprocessing.contracts.ReprocessingNavigationFacts;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingKnowledgeBases;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReprocessingHistoryServiceTest {

    private final SchemaReprocessingPlanRepository plans = mock(SchemaReprocessingPlanRepository.class);
    private final ReprocessingKnowledgeBases knowledgeBases = mock(ReprocessingKnowledgeBases.class);
    private final StoredSchemaSnapshots schemas = mock(StoredSchemaSnapshots.class);
    private final ReprocessingHistoryService history = new ReprocessingHistoryService(plans, knowledgeBases, schemas);

    @Test
    void targetCurrentBatchesByKnowledgeBaseAndReturnsFalseForMissingOrChangedTargets() {
        List<SchemaReprocessingPlanNode> values = List.of(
            plan("plan-current-1", "draft-1", "kb-current", "schema-current", "hash-current"),
            plan("plan-current-2", "draft-2", "kb-current", "schema-current", "hash-current"),
            plan("plan-changed-hash", "draft-3", "kb-current", "schema-current", "old-hash"),
            plan("plan-missing-schema", "draft-4", "kb-missing-schema", "schema-missing", "hash-missing"),
            plan("plan-missing-knowledge-base", "draft-5", "kb-missing", "schema-absent", "hash-absent")
        );
        when(knowledgeBases.find("kb-current")).thenReturn(Optional.of(
            new ReprocessingKnowledgeBases.KnowledgeBase("kb-current", "schema-current")));
        when(knowledgeBases.find("kb-missing-schema")).thenReturn(Optional.of(
            new ReprocessingKnowledgeBases.KnowledgeBase("kb-missing-schema", "schema-missing")));
        when(knowledgeBases.find("kb-missing")).thenReturn(Optional.empty());
        when(schemas.associated("kb-current")).thenReturn(List.of(snapshot("kb-current", "schema-current", "hash-current")));
        when(schemas.associated("kb-missing-schema")).thenReturn(List.of());

        List<String> draftIds = values.stream().map(SchemaReprocessingPlanNode::getDraftId).toList();
        when(plans.findLatestForDraftIds(draftIds)).thenReturn(values);
        Map<String, Boolean> currentness = history.latest(draftIds).values().stream()
            .collect(java.util.stream.Collectors.toMap(ReprocessingNavigationFacts.Reference::id,
                ReprocessingNavigationFacts.Reference::current));

        assertThat(currentness).containsExactlyInAnyOrderEntriesOf(Map.of(
            "plan-current-1", true,
            "plan-current-2", true,
            "plan-changed-hash", false,
            "plan-missing-schema", false,
            "plan-missing-knowledge-base", false
        ));
        verify(knowledgeBases, times(1)).find("kb-current");
        verify(knowledgeBases, times(1)).find("kb-missing-schema");
        verify(knowledgeBases, times(1)).find("kb-missing");
        verify(schemas, times(1)).associated("kb-current");
        verify(schemas, times(1)).associated("kb-missing-schema");
        verifyNoMoreInteractions(knowledgeBases, schemas);
    }

    @Test
    void latestKeepsReferencesForNonDefaultReasonsAndMarksStaleTargetCurrentness() {
        SchemaReprocessingPlanNode migration = plan(
            "plan-migration", "draft-migration", "kb-current", "schema-current", "hash-current");
        migration.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        migration.setStatus(SchemaReprocessingPlanStatus.PARTIAL);
        SchemaReprocessingPlanNode activation = plan(
            "plan-activation", "draft-activation", "kb-changed", "schema-old", "hash-old");
        activation.setReason(ReprocessingPlanReason.SCHEMA_ACTIVATION);
        activation.setStatus(SchemaReprocessingPlanStatus.COMPLETED);
        when(plans.findLatestForDraftIds(List.of("draft-migration", "draft-activation")))
            .thenReturn(List.of(migration, activation));
        when(knowledgeBases.find("kb-current")).thenReturn(Optional.of(
            new ReprocessingKnowledgeBases.KnowledgeBase("kb-current", "schema-current")));
        when(knowledgeBases.find("kb-changed")).thenReturn(Optional.of(
            new ReprocessingKnowledgeBases.KnowledgeBase("kb-changed", "schema-new")));
        when(schemas.associated("kb-current")).thenReturn(List.of(snapshot("kb-current", "schema-current", "hash-current")));
        when(schemas.associated("kb-changed")).thenReturn(List.of(snapshot("kb-changed", "schema-new", "hash-new")));

        Map<String, ReprocessingNavigationFacts.Reference> latest =
            history.latest(List.of("draft-migration", "draft-activation"));

        assertThat(latest).containsExactlyInAnyOrderEntriesOf(Map.of(
            "draft-migration", new ReprocessingNavigationFacts.Reference(
                "plan-migration", "PARTIAL", true,
                "/api/v1/knowledge-bases/kb-current/reprocessing-plans/plan-migration"),
            "draft-activation", new ReprocessingNavigationFacts.Reference(
                "plan-activation", "COMPLETED", false,
                "/api/v1/knowledge-bases/kb-changed/reprocessing-plans/plan-activation")
        ));
        verify(plans).findLatestForDraftIds(List.of("draft-migration", "draft-activation"));
    }

    private SchemaReprocessingPlanNode plan(
        String id, String draftId, String knowledgeBaseId, String schemaId, String schemaContentHash
    ) {
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setId(id);
        plan.setDraftId(draftId);
        plan.setKnowledgeBaseId(knowledgeBaseId);
        plan.setSchemaId(schemaId);
        plan.setSchemaContentHash(schemaContentHash);
        plan.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        plan.setStatus(SchemaReprocessingPlanStatus.FAILED);
        return plan;
    }

    private SchemaSnapshot snapshot(String knowledgeBaseId, String schemaId, String contentHash) {
        Instant timestamp = Instant.parse("2026-09-01T00:00:00Z");
        return new SchemaSnapshot(
            knowledgeBaseId,
            schemaId,
            "schema",
            1,
            SchemaSourceType.PREDEFINED,
            SchemaFormat.JSON,
            SchemaStatus.ACTIVE,
            "{}",
            contentHash,
            timestamp,
            timestamp,
            null
        );
    }
}
