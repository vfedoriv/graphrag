package io.github.vfedoriv.graphrag.documents.adapters.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class NativeGraphOutputTest {
    @Test void emptyAndRecursiveValuesPreservePublicShapesAndPrecision() throws Exception {
        NativeGraphOutput codec = new NativeGraphOutput();
        assertThat(codec.decode("{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[],\"relationships\":[]}")).isNotNull();
        io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult result = codec.decode("{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[{\"label\":\"Thing\",\"properties\":[{\"key\":\"v\",\"value\":{\"entries\":[{\"key\":\"nested\",\"value\":[true,null,9007199254740993,1.25,{\"entries\":[]}]}]}}],\"confidence\":null,\"extra\":\"ignored\"}],\"relationships\":[]}");
        Map<?, ?> nested = (Map<?, ?>) result.nodes().getFirst().properties().get("v");
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

    @Test void compositeEndpointsAndExplicitNullEntriesRemainOrdinaryMaps() {
        io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult result = new NativeGraphOutput().decode("""
            {"contractVersion":"graph-extraction-v1","nodes":[],"relationships":[{
             "type":"LINK","fromLabel":"A","fromKey":[{"key":"id","value":"A-1"},{"key":"tenant","value":42}],
             "toLabel":"B","toKey":[{"key":"id","value":123456789012345678901234567890},{"key":"nullable","value":null}],
             "properties":[],"confidence":null}]}
            """);
        assertThat(result.relationships().getFirst().fromKey()).containsEntry("id", "A-1").containsEntry("tenant", 42);
        Map<String, Object> target = result.relationships().getFirst().toKey();
        assertThat(target.get("id")).isEqualTo(new java.math.BigInteger("123456789012345678901234567890"));
        assertThat(target).containsEntry("nullable", null).doesNotContainKey("absent");
        assertThat(result.relationships().getFirst().properties()).isEmpty();
        assertThat(result.relationships().getFirst().confidence()).isNull();
    }

    @Test void undeclaredNativeKeysAreFilteredWithoutLoggingTheirContent(org.springframework.boot.test.system.CapturedOutput output) {
        io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult result = new NativeGraphOutput().decode("""
            {"contractVersion":"graph-extraction-v1","nodes":[{"label":"Contract","properties":[
              {"key":"contractId","value":"C-1"},{"key":"UNTRUSTED_KEY_SENTINEL","value":"PAYLOAD_SENTINEL"}],
              "confidence":null}],"relationships":[]}
            """);
        io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument schema = new io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument(
            "contracts",1,"",List.of(new io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument.NodeDefinition(
                "Contract","",List.of("contractId"),List.of(new io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument.PropertyDefinition("contractId","string",true)))),
            List.of(),List.of(),List.of());
        org.springframework.data.neo4j.core.Neo4jClient client = org.mockito.Mockito.mock(
            org.springframework.data.neo4j.core.Neo4jClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(client.query(org.mockito.ArgumentMatchers.anyString())
            .bind("chunk").to("chunkId").bind("kb").to("knowledgeBaseId").bind("doc").to("documentId")
            .fetchAs(Boolean.class).one()).thenReturn(java.util.Optional.of(true));
        io.github.vfedoriv.graphrag.documents.adapters.graph.GraphWriteService writer =
            new io.github.vfedoriv.graphrag.documents.adapters.graph.GraphWriteService(client);
        writer.write("kb","run","schema","doc","chunk",schema,result);
        assertThat(output).doesNotContain("UNTRUSTED_KEY_SENTINEL", "PAYLOAD_SENTINEL");
    }

    @Test void ambiguousAndMalformedPayloadsAreRejected() {
        NativeGraphOutput codec = new NativeGraphOutput();
        for (String input : List.of(
            "{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[],\"relationships\":[]} trailing",
            "{\"contractVersion\":\"x\",\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[],\"relationships\":[]}",
            "{\"contractVersion\":\"wrong\",\"nodes\":[],\"relationships\":[]}",
            "{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[{\"label\":\"Thing\",\"properties\":[{\"key\":\"x\",\"value\":1},{\"key\":\"x\",\"value\":2}],\"confidence\":null}],\"relationships\":[]}",
            "{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[{\"label\":\"Thing\",\"properties\":[{\"key\":\"x\"}],\"confidence\":null}],\"relationships\":[]}",
            "{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[{\"label\":\"Thing\",\"properties\":[{\"key\":1,\"value\":2}],\"confidence\":null}],\"relationships\":[]}",
            "{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[{\"label\":\"Thing\",\"properties\":[{\"key\":\"x\",\"value\":{\"raw\":3}}],\"confidence\":null}],\"relationships\":[]}")) {
            assertThatThrownBy(() -> codec.decode(input)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void schemaIsClosedRequiredAndRecursiveWithAnObjectRoot() throws Exception {
        JsonNode root = new ObjectMapper().readTree(NativeGraphOutput.SCHEMA);
        assertThat(root.path("type").asText()).isEqualTo("object");
        assertThat(root.has("anyOf")).isFalse();
        assertThat(root.path("properties").path("contractVersion").path("enum").get(0).asText())
            .isEqualTo("graph-extraction-v1");
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
