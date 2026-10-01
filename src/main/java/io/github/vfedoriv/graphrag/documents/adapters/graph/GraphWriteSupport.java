package io.github.vfedoriv.graphrag.documents.adapters.graph;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class GraphWriteSupport {

    private GraphWriteSupport() {
    }

    static String stableNodeId(String schemaId, String label, List<String> keyNames, Map<String, Object> keyProperties) {
        requireCompleteIdentity(label, keyNames, keyProperties);
        List<String> canonical = new ArrayList<>();
        canonical.add("schema:" + schemaId);
        canonical.add("label:" + label);
        for (String keyName : keyNames) {
            Object value = keyProperties == null ? null : keyProperties.get(keyName);
            String normalized = value == null ? "" : String.valueOf(value);
            canonical.add("key:" + keyName + "=" + normalized.length() + ":" + normalized);
        }
        return "node:" + sha256(String.join("\n", canonical));
    }

    static String stableRelationshipId(String schemaId, String type, String fromId, String toId) {
        List<String> canonical = List.of(
            "schema:" + schemaId,
            "type:" + type,
            "from:" + fromId,
            "to:" + toId
        );
        return "rel:" + sha256(String.join("\n", canonical));
    }

    static String stableEvidenceId(
        String factKind,
        String extractionRunId,
        String documentId,
        String chunkId,
        String canonicalFactId
    ) {
        List<String> canonical = List.of(
            "kind:" + factKind,
            "run:" + extractionRunId,
            "document:" + documentId,
            "chunk:" + chunkId,
            "fact:" + canonicalFactId
        );
        return "evidence:" + sha256(String.join("\n", canonical));
    }

    static void requireCompleteIdentity(String label, List<String> keyNames, Map<String, Object> keyProperties) {
        if (keyNames == null || keyNames.isEmpty()) {
            throw new IllegalArgumentException("Missing schema key definition for node label: " + label);
        }
        for (String keyName : keyNames) {
            Object value = keyProperties == null ? null : keyProperties.get(keyName);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalArgumentException("Incomplete identity material for node label: " + label + "." + keyName);
            }
        }
    }

    static Map<String, Object> filterDeclaredProperties(Map<String, Object> input, Set<String> allowed) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (input == null || input.isEmpty()) {
            return filtered;
        }
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (allowed.contains(key)) {
                filtered.put(key, entry.getValue());
            }
        }
        return filtered;
    }

    static Set<String> droppedPropertyNames(Map<String, Object> input, Set<String> allowed) {
        Set<String> dropped = new LinkedHashSet<>();
        if (input == null || input.isEmpty()) {
            return dropped;
        }
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (!allowed.contains(key)) {
                dropped.add(key);
            }
        }
        return dropped;
    }

    static Set<String> allowedNodeProperties(SchemaDocument.NodeDefinition nodeDef) {
        Set<String> properties = new LinkedHashSet<>();
        if (nodeDef == null || nodeDef.properties() == null) {
            return properties;
        }
        for (SchemaDocument.PropertyDefinition propertyDefinition : nodeDef.properties()) {
            if (propertyDefinition != null && propertyDefinition.name() != null && !propertyDefinition.name().isBlank()) {
                properties.add(propertyDefinition.name());
            }
        }
        return properties;
    }

    static Set<String> allowedRelationshipProperties(SchemaDocument schema, String type, String from, String to) {
        Set<String> properties = new LinkedHashSet<>();
        if (schema == null || schema.relationships() == null) {
            return properties;
        }
        for (SchemaDocument.RelationshipDefinition relationshipDefinition : schema.relationships()) {
            if (relationshipDefinition == null) {
                continue;
            }
            if (!Objects.equals(type, relationshipDefinition.type())
                || !Objects.equals(from, relationshipDefinition.from())
                || !Objects.equals(to, relationshipDefinition.to())) {
                continue;
            }
            List<SchemaDocument.PropertyDefinition> relationshipProperties = relationshipDefinition.properties();
            if (relationshipProperties == null) {
                return properties;
            }
            for (SchemaDocument.PropertyDefinition propertyDefinition : relationshipProperties) {
                if (propertyDefinition != null && propertyDefinition.name() != null && !propertyDefinition.name().isBlank()) {
                    properties.add(propertyDefinition.name());
                }
            }
            return properties;
        }
        return properties;
    }

    static String safeToken(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe schema token: " + value);
        }
        return value;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
