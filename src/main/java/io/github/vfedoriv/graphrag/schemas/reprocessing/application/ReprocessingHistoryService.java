package io.github.vfedoriv.graphrag.schemas.reprocessing.application;

import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.reprocessing.contracts.ReprocessingNavigationFacts;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingKnowledgeBases;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingPlanRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReprocessingHistoryService implements ReprocessingNavigationFacts {
    private final SchemaReprocessingPlanRepository plans;
    private final ReprocessingKnowledgeBases knowledgeBases;
    private final StoredSchemaSnapshots schemas;

    public ReprocessingHistoryService(SchemaReprocessingPlanRepository plans, ReprocessingKnowledgeBases knowledgeBases, StoredSchemaSnapshots schemas) {
        this.plans = plans;
        this.knowledgeBases = knowledgeBases;
        this.schemas = schemas;
    }

    @RelationalTransactional(readOnly = true)
    public boolean targetCurrent(SchemaReprocessingPlanNode plan) {
        String activeSchemaId = knowledgeBases.find(plan.getKnowledgeBaseId()).map(ReprocessingKnowledgeBases.KnowledgeBase::activeSchemaId).orElse(null);
        SchemaSnapshot schema = schemas.findById(plan.getSchemaId()).orElse(null);
        return schema != null && plan.getSchemaId().equals(activeSchemaId) && plan.getSchemaContentHash().equals(schema.contentHash());
    }

    private Map<String, Boolean> targetCurrent(List<SchemaReprocessingPlanNode> values) {
        if (values.isEmpty()) return Map.of();
        Map<String, String> activeSchemas = new HashMap<>();
        values.stream().map(SchemaReprocessingPlanNode::getKnowledgeBaseId).distinct().forEach(id ->
            knowledgeBases.find(id).map(ReprocessingKnowledgeBases.KnowledgeBase::activeSchemaId).ifPresent(active -> activeSchemas.put(id, active)));
        Map<String, SchemaSnapshot> stored = activeSchemas.keySet().stream().flatMap(id -> schemas.associated(id).stream())
            .collect(Collectors.toMap(SchemaSnapshot::schemaDefinitionId, Function.identity(), (left, right) -> left));
        return values.stream().collect(Collectors.toMap(SchemaReprocessingPlanNode::getId, plan -> {
            SchemaSnapshot schema = stored.get(plan.getSchemaId());
            return schema != null && plan.getSchemaId().equals(activeSchemas.get(plan.getKnowledgeBaseId()))
                && plan.getSchemaContentHash().equals(schema.contentHash());
        }));
    }

    @RelationalTransactional(readOnly = true)
    @Override public Map<String, Reference> latest(List<String> draftIds) {
        if (draftIds.isEmpty()) return Map.of();
        List<SchemaReprocessingPlanNode> values = plans.findLatestForDraftIds(draftIds);
        Map<String, Boolean> current = targetCurrent(values);
        Map<String, Reference> result = new HashMap<>();
        values.forEach(plan -> result.put(plan.getDraftId(), new Reference(plan.getId(), plan.getStatus().name(),
            current.getOrDefault(plan.getId(), false), "/api/v1/knowledge-bases/" + plan.getKnowledgeBaseId() + "/reprocessing-plans/" + plan.getId())));
        return Map.copyOf(result);
    }
}
