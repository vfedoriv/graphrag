package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchResultDtos.AdvancedSearchResultV1;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.Answer;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.Evidence;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.GraphFact;
import io.github.vfedoriv.graphrag.service.AdvancedSearchCitationCatalog.Catalog;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AdvancedSearchResultCodec {
    public static final int PAYLOAD_VERSION = 1;
    private final ObjectMapper objectMapper;
    public AdvancedSearchResultCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public String write(JsonNode result, int evidenceCount, int maximumEvidence) {
        if (result == null || !result.isObject()) {
            throw new IllegalArgumentException("Advanced-search result must be a JSON object");
        }
        if (result.path("payloadVersion").asInt(-1) != PAYLOAD_VERSION) {
            throw new IllegalArgumentException("Unsupported advanced-search result payload version");
        }
        if (evidenceCount < 0 || evidenceCount > maximumEvidence || evidenceCount > 20) {
            throw new IllegalArgumentException("Advanced-search result exceeds its bounded evidence envelope");
        }
        AdvancedSearchResultV1 typed = decode(result);
        validate(result, typed, evidenceCount);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Advanced-search result is not serializable", exception);
        }
    }

    public AdvancedSearchResultV1 read(String json) {
        try {
            JsonNode value = objectMapper.readTree(json);
            if (!value.isObject()) {
                throw new IllegalArgumentException("Stored advanced-search result is invalid");
            }
            if (value.path("payloadVersion").asInt(-1) != PAYLOAD_VERSION) {
                throw new IllegalArgumentException("Unsupported advanced-search result payload version");
            }
            AdvancedSearchResultV1 typed = decode(value);
            validate(value, typed, value.path("evidence").size());
            return typed;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored advanced-search result is invalid", exception);
        }
    }

    private AdvancedSearchResultV1 decode(JsonNode result) {
        try {
            return objectMapper.treeToValue(result, AdvancedSearchResultV1.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Advanced-search result structure is invalid", exception);
        }
    }

    private void validate(JsonNode result, AdvancedSearchResultV1 typed, int evidenceCount) {
        try {
            if (typed.payloadVersion() != PAYLOAD_VERSION || typed.answer() == null
                || typed.answerDiagnostics() == null || typed.diagnostics() == null
                || !result.path("evidence").isArray() || !result.path("contexts").isArray()
                || !result.path("graphFacts").isArray()) {
                throw new IllegalArgumentException("Advanced-search result structure is invalid");
            }
            if (evidenceCount < 0 || evidenceCount > 20) {
                throw new IllegalArgumentException("Advanced-search result exceeds its bounded evidence envelope");
            }
            List<Evidence> evidence = objectMapper.convertValue(
                result.path("evidence"), new TypeReference<List<Evidence>>() { });
            List<Evidence> contexts = objectMapper.convertValue(
                result.path("contexts"), new TypeReference<List<Evidence>>() { });
            List<GraphFact> graphFacts = objectMapper.convertValue(
                result.path("graphFacts"), new TypeReference<List<GraphFact>>() { });
            if (evidence.size() != evidenceCount) {
                throw new IllegalArgumentException("Advanced-search result evidence count is inconsistent");
            }
            Map<String, Set<String>> factCitations = new LinkedHashMap<>();
            for (GraphFact fact : graphFacts) {
                factCitations.put(fact.factId(), new LinkedHashSet<>(fact.citationIds()));
            }
            Catalog catalog = new Catalog(evidence, contexts, graphFacts, factCitations);
            Answer answer = objectMapper.treeToValue(result.path("answer"), Answer.class);
            AdvancedSearchAnswerValidator.Validation validation =
                new AdvancedSearchAnswerValidator().validate(answer, catalog);
            if (!validation.valid()) {
                throw new IllegalArgumentException(
                    "Advanced-search result answer is invalid: " + String.join(",", validation.errors()));
            }
            if (!result.path("answerDiagnostics").isObject() || !result.path("diagnostics").isObject()) {
                throw new IllegalArgumentException("Advanced-search result diagnostics are invalid");
            }
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Advanced-search result structure is invalid", exception);
        }
    }
}
