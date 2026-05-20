package io.github.vfedoriv.graphrag.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NodeKeySupportTest {

    @Test
    void normalizedKeys_handlesNullAndBlankAndDeduplicatesInOrder() {
        assertThat(NodeKeySupport.normalizedKeys(null)).isEmpty();
        SchemaDocument.NodeDefinition node = new SchemaDocument.NodeDefinition(
            "Person",
            "",
            List.of("  fullName ", "", "birthDate", "fullName", " "),
            List.of()
        );
        assertThat(NodeKeySupport.normalizedKeys(node)).containsExactly("fullName", "birthDate");
    }

    @Test
    void display_formatsEmptySingleAndComposite() {
        assertThat(NodeKeySupport.display(null)).isEqualTo("(none)");
        assertThat(NodeKeySupport.display(List.of())).isEqualTo("(none)");
        assertThat(NodeKeySupport.display(List.of("id"))).isEqualTo("id");
        assertThat(NodeKeySupport.display(List.of("fullName", "birthDate"))).isEqualTo("fullName, birthDate");
    }
}
