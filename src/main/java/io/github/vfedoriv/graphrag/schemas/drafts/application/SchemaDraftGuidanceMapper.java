package io.github.vfedoriv.graphrag.schemas.drafts.application;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryRequest.DiscoveryGuidance;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.DraftGuidance;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SchemaDraftGuidanceMapper {
    private static final Set<String> STRUCTURED_FIELDS = Set.of(
        "domainDescription", "intendedQuestions", "requiredConcepts", "preferredConcepts",
        "excludedConcepts", "namingRules", "propertyRules", "relationshipRules");
    private static final Set<String> WRAPPER_FIELDS = Set.of("additionalInstructions", "guidance");

    private final SchemaDraftJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    public SchemaDraftGuidanceMapper(SchemaDraftJsonSupport jsonSupport, ObjectMapper objectMapper) {
        this.jsonSupport = jsonSupport;
        this.objectMapper = objectMapper;
    }

    public DraftGuidance read(String persistedJson) {
        JsonNode root = jsonSupport.parse(persistedJson);
        if (!root.isObject()) {
            throw invalidPersistedGuidance();
        }
        if (root.has("guidance")) {
            requireOnly(root, WRAPPER_FIELDS);
            JsonNode guidanceNode = root.get("guidance");
            DiscoveryGuidance guidance = guidanceNode == null || guidanceNode.isNull()
                ? DiscoveryGuidance.empty() : readStructured(guidanceNode);
            String instructions = textOrNull(root.get("additionalInstructions"));
            return new DraftGuidance(instructions, guidance);
        }
        requireOnly(root, union(STRUCTURED_FIELDS, "additionalInstructions"));
        ObjectNode structured = ((ObjectNode) root).deepCopy();
        JsonNode instructionsNode = structured.remove("additionalInstructions");
        return new DraftGuidance(textOrNull(instructionsNode), readStructured(structured));
    }

    public String canonical(DraftGuidance guidance) {
        return jsonSupport.canonical(guidance == null ? DraftGuidance.empty() : guidance);
    }

    private DiscoveryGuidance readStructured(JsonNode value) {
        if (!value.isObject()) {
            throw invalidPersistedGuidance();
        }
        requireOnly(value, STRUCTURED_FIELDS);
        try {
            return objectMapper.treeToValue(value, DiscoveryGuidance.class);
        } catch (Exception exception) {
            throw invalidPersistedGuidance();
        }
    }

    private void requireOnly(JsonNode value, Set<String> allowed) {
        value.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw invalidPersistedGuidance();
            }
        });
    }

    private String textOrNull(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidPersistedGuidance();
        }
        return value.asText();
    }

    private Set<String> union(Set<String> fields, String extra) {
        java.util.HashSet<String> result = new java.util.HashSet<>(fields);
        result.add(extra);
        return Set.copyOf(result);
    }

    private IllegalStateException invalidPersistedGuidance() {
        return new IllegalStateException("Persisted draft guidance has an unsupported shape");
    }
}
