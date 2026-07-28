package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType;
import io.github.vfedoriv.graphrag.infrastructure.persistence.SchemaDraftGraphService;
import io.github.vfedoriv.graphrag.repository.SchemaDraftConflictRepository;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;

@Service
public class SchemaDraftConflictService {
    private final SchemaDraftConflictRepository conflictRepository;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftGraphService graphService;
    private final ObjectMapper objectMapper;

    public SchemaDraftConflictService(
        SchemaDraftConflictRepository conflictRepository,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftGraphService graphService,
        ObjectMapper objectMapper
    ) {
        this.conflictRepository = conflictRepository;
        this.jsonSupport = jsonSupport;
        this.graphService = graphService;
        this.objectMapper = objectMapper;
    }

    @GraphTransactional
    public SchemaDraftConflictNode create(
        String draftId, String aggregateRevisionId, SchemaDraftConflictType type,
        String coordinate, Object alternatives, Object evidence
    ) {
        SchemaDraftConflictNode conflict = new SchemaDraftConflictNode();
        conflict.setId(UUID.randomUUID().toString());
        conflict.setDraftId(draftId);
        conflict.setAggregateRevisionId(aggregateRevisionId);
        conflict.setType(type);
        conflict.setCoordinate(coordinate);
        conflict.setAlternativesJson(jsonSupport.canonical(alternatives));
        conflict.setEvidenceJson(jsonSupport.canonical(evidence));
        conflict.setSemanticKey(semanticKey(type, coordinate, conflict.getAlternativesJson()));
        conflict.setCreatedAt(Instant.now());
        carryLatestResolution(conflict);
        SchemaDraftConflictNode saved = conflictRepository.save(conflict);
        graphService.attach(draftId, "SchemaDraftConflict", saved.getId());
        return saved;
    }

    public void resolve(
        SchemaDraftConflictNode conflict, String selectedAlternative, Object customResolution
    ) {
        JsonNode alternatives = jsonSupport.parse(conflict.getAlternativesJson());
        JsonNode custom = customResolution == null ? null : objectMapper.valueToTree(customResolution);
        if (!validResolution(alternatives, selectedAlternative, custom, customResolution != null)) {
            throw new IllegalArgumentException("Choose exactly one valid alternative or a valid custom resolution");
        }
        conflict.setResolved(true);
        conflict.setSelectedAlternative(selectedAlternative);
        conflict.setCustomResolutionJson(custom == null ? null : jsonSupport.canonical(custom));
        conflict.setResolvedAt(Instant.now());
    }

    public String semanticKey(
        SchemaDraftConflictType type, String coordinate, Object alternatives
    ) {
        return semanticKey(type, coordinate, jsonSupport.canonical(alternatives));
    }

    private String semanticKey(
        SchemaDraftConflictType type, String coordinate, String alternativesJson
    ) {
        JsonNode alternatives = jsonSupport.parse(alternativesJson);
        TreeSet<String> canonicalAlternatives = new TreeSet<>();
        if (alternatives.isArray()) {
            alternatives.forEach(value -> canonicalAlternatives.add(jsonSupport.canonical(value)));
        } else {
            canonicalAlternatives.add(jsonSupport.canonical(alternatives));
        }
        ObjectNode identity = objectMapper.createObjectNode();
        identity.put("type", type.name());
        if (coordinate == null) {
            identity.putNull("coordinate");
        } else {
            identity.put("coordinate", coordinate);
        }
        ArrayNode normalized = identity.putArray("alternatives");
        canonicalAlternatives.forEach(normalized::add);
        return jsonSupport.fingerprint(identity);
    }

    private void carryLatestResolution(SchemaDraftConflictNode conflict) {
        List<SchemaDraftConflictNode> history = conflictRepository.findResolvedHistory(
            conflict.getDraftId(), conflict.getAggregateRevisionId());
        for (SchemaDraftConflictNode historical : history) {
            String historicalKey = historical.getSemanticKey();
            if (historicalKey == null || historicalKey.isBlank()) {
                historicalKey = semanticKey(
                    historical.getType(), historical.getCoordinate(), historical.getAlternativesJson());
            }
            if (!conflict.getSemanticKey().equals(historicalKey)) {
                continue;
            }
            applyHistoricalResolution(conflict, historical);
            return;
        }
    }

    private void applyHistoricalResolution(
        SchemaDraftConflictNode conflict, SchemaDraftConflictNode historical
    ) {
        JsonNode alternatives = jsonSupport.parse(conflict.getAlternativesJson());
        JsonNode custom = historicalCustomResolution(historical);
        if (!validResolution(alternatives, historical.getSelectedAlternative(), custom,
            historical.getCustomResolutionJson() != null)) {
            return;
        }
        conflict.setResolved(true);
        conflict.setSelectedAlternative(historical.getSelectedAlternative());
        conflict.setCustomResolutionJson(custom == null ? null : jsonSupport.canonical(custom));
        conflict.setResolvedAt(Instant.now());
    }

    private JsonNode historicalCustomResolution(SchemaDraftConflictNode historical) {
        if (historical.getCustomResolutionJson() == null) {
            return null;
        }
        try {
            return jsonSupport.parse(historical.getCustomResolutionJson());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean validResolution(
        JsonNode alternatives, String selectedAlternative, JsonNode customResolution,
        boolean customProvided
    ) {
        boolean selectedProvided = selectedAlternative != null;
        if (selectedProvided == customProvided) {
            return false;
        }
        if (selectedProvided) {
            return alternatives.isArray()
                && java.util.stream.StreamSupport.stream(alternatives.spliterator(), false)
                    .anyMatch(value -> selectedAlternative.equals(value.asText()));
        }
        return validCustomResolution(customResolution);
    }

    private boolean validCustomResolution(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.textValue().isBlank();
        }
        if (value.isContainerNode()) {
            return !value.isEmpty();
        }
        return true;
    }
}
