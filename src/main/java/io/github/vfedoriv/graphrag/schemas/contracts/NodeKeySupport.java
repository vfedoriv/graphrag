package io.github.vfedoriv.graphrag.schemas.contracts;

import java.util.List;
import java.util.stream.Collectors;

public final class NodeKeySupport {

    private NodeKeySupport() {
    }

    public static List<String> normalizedKeys(SchemaDocument.NodeDefinition nodeDefinition) {
        if (nodeDefinition == null || nodeDefinition.key() == null) {
            return List.of();
        }
        return nodeDefinition.key().stream()
            .filter(keyName -> keyName != null && !keyName.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    public static String display(List<String> keyNames) {
        if (keyNames == null || keyNames.isEmpty()) {
            return "(none)";
        }
        if (keyNames.size() == 1) {
            return keyNames.getFirst();
        }
        return keyNames.stream().collect(Collectors.joining(", "));
    }
}
