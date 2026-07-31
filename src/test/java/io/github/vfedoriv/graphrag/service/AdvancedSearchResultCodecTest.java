package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class AdvancedSearchResultCodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdvancedSearchResultCodec codec = new AdvancedSearchResultCodec(objectMapper);

    @Test
    void validatesVersionObjectShapeAndEvidenceBoundOnWriteAndRead() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("payloadVersion", 1); result.putArray("evidence");
        String json = codec.write(result, 0, 10);
        assertThat(codec.read(json)).isEqualTo(result);
        assertThatThrownBy(() -> codec.write(result, 11, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.read("{\"payloadVersion\":2}"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
