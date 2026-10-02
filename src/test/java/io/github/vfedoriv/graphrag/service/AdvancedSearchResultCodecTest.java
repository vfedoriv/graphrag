package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.search.runs.adapters.codec.AdvancedSearchResultCodec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchResultDtos.AdvancedSearchResultV1;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedSearchResultCodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdvancedSearchResultCodec codec = new AdvancedSearchResultCodec(objectMapper);

    @Test
    void validatesVersionObjectShapeAndEvidenceBoundOnWriteAndRead() {
        ObjectNode result = payload("INSUFFICIENT_EVIDENCE", false, false);
        String json = codec.write(result, 0, 10);
        AdvancedSearchResultV1 decoded = codec.read(json);
        assertThat(decoded.payloadVersion()).isEqualTo(1);
        assertThat(decoded.answer().status().name()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThatThrownBy(() -> codec.write(result, 11, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.read("{\"payloadVersion\":2}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAllTerminalAnswerShapesAndLegacyNullableSourceMetadata() {
        ObjectNode completed = payload("ANSWERED", true, true);
        ObjectNode partial = payload("ANSWERED", true, true);
        ObjectNode insufficient = payload("INSUFFICIENT_EVIDENCE", false, false);
        ObjectNode unavailable = payload("ANSWER_UNAVAILABLE", false, false);

        assertThat(codec.read(codec.write(completed, 1, 10)).answer().status().name()).isEqualTo("ANSWERED");
        assertThat(codec.read(codec.write(partial, 1, 10)).answer().status().name()).isEqualTo("ANSWERED");
        assertThat(codec.read(codec.write(insufficient, 0, 10)).answer().status().name())
            .isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(codec.read(codec.write(unavailable, 0, 10)).answer().status().name())
            .isEqualTo("ANSWER_UNAVAILABLE");
        assertThat(codec.read(codec.write(completed, 1, 10)).evidence().getFirst().sourceFilename()).isNull();
    }

    private ObjectNode payload(String status, boolean withEvidence, boolean withClaim) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("payloadVersion", 1);
        Map<String, Object> answer = new java.util.LinkedHashMap<>();
        answer.put("version", 1);
        answer.put("status", status);
        answer.put("text", status.equals("ANSWERED") ? "The answer." : "No validated answer.");
        answer.put("confidence", Map.of("level", status.equals("ANSWERED") ? "HIGH" : "LOW", "score", status.equals("ANSWERED") ? 0.9 : 0.0));
        answer.put("limitations", List.of(Map.of("code", "TEST", "description", "Test limitation.")));
        answer.put("claims", withClaim
            ? List.of(Map.of("id", "C1", "kind", "TEXT", "text", "The answer.", "citationIds", List.of("E1"), "graphFactIds", List.of(), "graphEvidenceIds", List.of()))
            : List.of());
        result.set("answer", objectMapper.valueToTree(answer));
        if (withEvidence) {
            result.set("evidence", objectMapper.valueToTree(List.of(Map.of(
                "citationId", "E1", "type", "TEXT_CHILD", "chunkId", "chunk-1", "documentId", "doc-1",
                "range", Map.of("sourceStart", 1, "sourceEnd", 10), "text", "Evidence", "rank", 1, "score", 0.9))));
        } else {
            result.putArray("evidence");
        }
        result.putArray("contexts");
        result.putArray("graphFacts");
        result.putObject("answerDiagnostics");
        result.putObject("diagnostics");
        return result;
    }
}
