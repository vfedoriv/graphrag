package io.github.vfedoriv.graphrag.schemas.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SchemaSnapshotTest {

    @Test
    void publicContractExposesOnlyValues() {
        assertThat(Arrays.stream(SchemaSnapshot.class.getRecordComponents())
            .map(component -> component.getType().getPackageName()))
            .allMatch(packageName -> !packageName.contains("persistence")
                && !packageName.contains("repository")
                && !packageName.contains("client"));
    }

    @Test
    void copiesNestedSchemaCollections() {
        List<String> keys = new ArrayList<>(List.of("id"));
        List<SchemaDocument.PropertyDefinition> properties = new ArrayList<>(
            List.of(new SchemaDocument.PropertyDefinition("id", "string", true)));
        List<SchemaDocument.NodeDefinition> nodes = new ArrayList<>(
            List.of(new SchemaDocument.NodeDefinition("Item", null, keys, properties)));
        SchemaDocument document = new SchemaDocument("items", 1, null, nodes,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        SchemaSnapshot snapshot = new SchemaSnapshot("kb-1", "schema-1", "items", 1,
            SchemaSourceType.PREDEFINED, SchemaFormat.JSON, SchemaStatus.ACTIVE,
            "{}", "stored-hash", null, null, document);
        keys.add("other");
        properties.clear();
        nodes.clear();

        assertThat(snapshot.schema().nodes()).hasSize(1);
        assertThat(snapshot.schema().nodes().getFirst().key()).containsExactly("id");
        assertThat(snapshot.schema().nodes().getFirst().properties()).hasSize(1);
        assertThatThrownBy(() -> snapshot.schema().nodes().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.schema().nodes().getFirst().key().add("other"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
