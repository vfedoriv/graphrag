package io.github.vfedoriv.graphrag.documents.adapters.graph;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphWriteSupportTest {

    @Test
    void shouldGenerateDeterministicNodeIdAndRejectIncompleteIdentity() {
        String first = GraphWriteSupport.stableNodeId("schema-1", "Contract", List.of("contractId"), Map.of("contractId", "C-1"));
        String second = GraphWriteSupport.stableNodeId("schema-1", "Contract", List.of("contractId"), Map.of("contractId", "C-1"));

        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> GraphWriteSupport.stableNodeId("schema-1", "Contract", List.of("contractId"), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Incomplete identity material");
    }

    @Test
    void shouldProduceDifferentNodeIdsForDelimiterLikeValues() {
        String first = GraphWriteSupport.stableNodeId("schema", "Contract", List.of("k"), Map.of("k", "a\n1"));
        String second = GraphWriteSupport.stableNodeId("schema", "Contract", List.of("k"), Map.of("k", "a\n11"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldFilterDeclaredPropertiesAndRejectUnsafeToken() {
        Map<String, Object> filtered = GraphWriteSupport.filterDeclaredProperties(
            Map.of("declared", "ok", "extra", "drop"),
            Set.of("declared")
        );

        assertThat(filtered).containsExactly(Map.entry("declared", "ok"));
        assertThat(GraphWriteSupport.droppedPropertyNames(Map.of("declared", "ok", "extra", "drop"), Set.of("declared")))
            .containsExactly("extra");
        assertThatThrownBy(() -> GraphWriteSupport.safeToken("Bad-Token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsafe schema token");
    }

    @Test
    void shouldIgnoreBlankOrNullPropertyNamesInFiltering() {
        Map<String, Object> filtered = GraphWriteSupport.filterDeclaredProperties(
            Map.of("declared", "ok", "", "bad"),
            Set.of("declared")
        );

        assertThat(filtered).containsExactly(Map.entry("declared", "ok"));
    }

    @Test
    void shouldAllowDeclaredRelationshipPropertiesOnlyForExactTriple() {
        SchemaDocument schema = new SchemaDocument(
            "contracts", 1, null, List.of(),
            List.of(new SchemaDocument.RelationshipDefinition(
                "HAS_PARTY", "Contract", "Party", null,
                List.of(new SchemaDocument.PropertyDefinition("role", "string", false))
            )),
            List.of(), List.of()
        );

        assertThat(GraphWriteSupport.allowedRelationshipProperties(schema, "HAS_PARTY", "Contract", "Party"))
            .containsExactly("role");
        assertThat(GraphWriteSupport.allowedRelationshipProperties(schema, "HAS_PARTY", "Contract", "Address"))
            .isEmpty();
    }

    @Test
    void shouldHandleNullSchemaAndPropertiesGracefully() {
        assertThat(GraphWriteSupport.allowedNodeProperties(null)).isEmpty();
        assertThat(GraphWriteSupport.allowedRelationshipProperties(null, "T", "A", "B")).isEmpty();
        assertThat(GraphWriteSupport.filterDeclaredProperties(null, Set.of("x"))).isEmpty();
        assertThat(GraphWriteSupport.droppedPropertyNames(null, Set.of("x"))).isEmpty();
    }

    @Test
    void shouldRejectMissingSchemaKeyDefinition() {
        assertThatThrownBy(() -> GraphWriteSupport.stableNodeId("schema-1", "Contract", List.of(), Map.of("id", "1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing schema key definition");
    }
}
