package io.github.vfedoriv.graphrag.search.query.adapters.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NativeCypherOutputTest {
    @Test void emptyAndRecursiveValuesPreservePublicShapesAndPrecision() throws Exception {
        NativeCypherOutput codec = new NativeCypherOutput();
        assertThat(codec.decode("{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[]}")).isNotNull();
        io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher result = codec.decode("{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[{\"key\":\"v\",\"value\":{\"entries\":[{\"key\":\"nested\",\"value\":[true,null,9007199254740993,1.25,{\"entries\":[]}]}]}}]}");
        Map<?, ?> nested = (Map<?, ?>) result.parameters().get("v");
        List<?> values = (List<?>) nested.get("nested");
        assertThat(values).hasSize(5);
        assertThat(values.get(0)).isEqualTo(true);
        assertThat(values.get(1)).isNull();
        assertThat(values.get(2).toString()).isEqualTo("9007199254740993");
        assertThat(values.get(2)).isInstanceOf(Number.class).isNotInstanceOf(Double.class);
        assertThat(values.get(3).toString()).isEqualTo("1.25");
        assertThat(org.neo4j.driver.Values.value(values.get(3)).asDouble()).isEqualTo(1.25);
        assertThat((Map<?, ?>) values.get(4)).isEmpty();
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("contractVersion", "entries");
    }

    @Test void ambiguousAndMalformedPayloadsAreRejected() {
        NativeCypherOutput codec = new NativeCypherOutput();
        for (String input : List.of(
            "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[]} trailing",
            "{\"contractVersion\":\"x\",\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[]}",
            "{\"contractVersion\":\"wrong\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[]}",
            "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[{\"key\":\"x\",\"value\":1},{\"key\":\"x\",\"value\":2}]}",
            "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[{\"key\":\"x\"}]}",
            "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[{\"key\":1,\"value\":2}]}",
            "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v\",\"explanation\":\"Read\",\"parameters\":[{\"key\":\"x\",\"value\":{\"raw\":3}}]}")) {
            assertThatThrownBy(() -> codec.decode(input)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void schemaIsClosedRequiredAndRecursiveWithAnObjectRoot() throws Exception {
        JsonNode root = new ObjectMapper().readTree(NativeCypherOutput.SCHEMA);
        assertThat(root.path("type").asText()).isEqualTo("object");
        assertThat(root.has("anyOf")).isFalse();
        assertThat(root.path("properties").path("contractVersion").path("enum").get(0).asText())
            .isEqualTo("cypher-generation-v1");
        assertThat(root.path("$defs").path("value").toString()).contains("#/$defs/value", "#/$defs/entry");
        verifyObjects(root);
    }
    private void verifyObjects(JsonNode node) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                assertThat(node.path("$ref").asText()).isIn("#/$defs/value", "#/$defs/entry");
            }
            if (node.path("type").asText().equals("object")) {
                assertThat(node.path("additionalProperties").isBoolean()).isTrue();
                assertThat(node.path("additionalProperties").asBoolean()).isFalse();
                assertThat(node.path("required").size()).isEqualTo(node.path("properties").size());
                node.path("properties").fieldNames().forEachRemaining(name -> {
                    assertThat(node.path("required").toString()).contains("\"" + name + "\"");
                });
            }
            node.elements().forEachRemaining(this::verifyObjects);
        } else if (node.isArray()) node.elements().forEachRemaining(this::verifyObjects);
    }
}
