package io.github.vfedoriv.graphrag.application.schema;

import io.github.vfedoriv.graphrag.dto.SchemaGenerationWarning;
import io.github.vfedoriv.graphrag.schemas.contracts.NodeKeySupport;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SchemaGenerationWarningFactory {

    public List<SchemaGenerationWarning> build(SchemaDocument schema) {
        List<SchemaGenerationWarning> warnings = new ArrayList<>();
        List<SchemaDocument.NodeDefinition> nodes = schema == null ? List.of() : schema.nodes();
        if (nodes == null || nodes.isEmpty()) {
            return warnings;
        }
        for (int index = 0; index < nodes.size(); index++) {
            SchemaDocument.NodeDefinition node = nodes.get(index);
            if (node == null) {
                continue;
            }
            List<String> keyNames = NodeKeySupport.normalizedKeys(node);
            List<SchemaDocument.PropertyDefinition> properties = node.properties();
            List<String> propertyNames = properties == null ? List.of() : properties.stream()
                .filter(Objects::nonNull)
                .map(SchemaDocument.PropertyDefinition::name)
                .filter(Objects::nonNull)
                .toList();
            String nodeLabel = node.label() == null ? "(unknown)" : node.label();
            if (keyNames.isEmpty()) {
                warnings.add(new SchemaGenerationWarning(index, nodeLabel, "NODE_KEY_MISSING",
                    "Node key is missing for node '%s'".formatted(nodeLabel),
                    List.of("Choose one or more existing properties as node key",
                        "Add canonical identity properties if current properties are not unique")));
                continue;
            }
            for (String keyName : keyNames) {
                if (!propertyNames.contains(keyName)) {
                    warnings.add(new SchemaGenerationWarning(index, nodeLabel, "NODE_KEY_PROPERTY_MISMATCH",
                        "Node key component '%s' is not declared in properties for node '%s'".formatted(keyName, nodeLabel),
                        List.of("Add property '%s' to node properties".formatted(keyName),
                            "Change node key to use existing property names")));
                }
            }
        }
        return warnings;
    }
}
