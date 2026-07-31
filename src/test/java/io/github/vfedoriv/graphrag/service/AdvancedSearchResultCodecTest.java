package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedSearchResultCodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdvancedSearchResultCodec codec = new AdvancedSearchResultCodec(objectMapper);

    @Test
    void validatesVersionObjectShapeAndEvidenceBoundOnWriteAndRead() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("payloadVersion", 1);
        result.set("answer", objectMapper.valueToTree(Map.of(
            "version", 1,
            "status", "INSUFFICIENT_EVIDENCE",
            "text", "Insufficient evidence.",
            "confidence", Map.of("level", "LOW", "score", 0.0),
            "limitations", List.of(Map.of("code", "NO_EVIDENCE", "description", "No evidence.")),
            "claims", List.of()
        )));
        result.putArray("evidence"); result.putArray("contexts"); result.putArray("graphFacts");
        result.putObject("answerDiagnostics"); result.putObject("diagnostics");
        String json = codec.write(result, 0, 10);
        assertThat(codec.read(json)).isEqualTo(result);
        assertThatThrownBy(() -> codec.write(result, 11, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.read("{\"payloadVersion\":2}"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
