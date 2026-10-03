package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.contracts.NodeKeySupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SchemaValidator {

    public List<String> validate(SchemaDocument schema) {
        List<String> errors = new ArrayList<>();
        if (schema == null) {
            errors.add("schema: must not be null");
            return errors;
        }
        if (isBlank(schema.name())) {
            errors.add("name: must not be blank");
        }
        if (schema.version() == null || schema.version() <= 0) {
            errors.add("version: must be > 0");
        }
        if (schema.nodes() == null || schema.nodes().isEmpty()) {
            errors.add("nodes: must not be empty");
        }
        if (schema.relationships() == null) {
            errors.add("relationships: must not be null");
        }

        Set<String> nodeLabels = new HashSet<>();
        if (schema.nodes() != null) {
            for (int i = 0; i < schema.nodes().size(); i++) {
                SchemaDocument.NodeDefinition node = schema.nodes().get(i);
                String path = "nodes[" + i + "]";
                if (isBlank(node.label())) {
                    errors.add(path + ".label: must not be blank");
                    continue;
                }
                if (!nodeLabels.add(node.label())) {
                    errors.add(path + ".label: duplicate label '" + node.label() + "'");
                }
                List<String> keyNames = NodeKeySupport.normalizedKeys(node);
                if (keyNames.isEmpty()) {
                    errors.add(path + ".key: must not be blank");
                    continue;
                }
                List<SchemaDocument.PropertyDefinition> properties = node.properties();
                if (properties == null || properties.isEmpty()) {
                    errors.add(path + ".properties: key '" + NodeKeySupport.display(keyNames) + "' must be declared as a property");
                    continue;
                }
                Set<String> propertyNames = new HashSet<>();
                for (SchemaDocument.PropertyDefinition property : properties) {
                    if (property != null && !isBlank(property.name())) {
                        propertyNames.add(property.name());
                    }
                }
                for (String keyName : keyNames) {
                    if (!propertyNames.contains(keyName)) {
                        errors.add(path + ".key: '" + keyName + "' is missing from declared properties");
                    }
                }
            }
        }

        Set<String> relationTypes = new HashSet<>();
        if (schema.relationships() != null) {
            for (int i = 0; i < schema.relationships().size(); i++) {
                SchemaDocument.RelationshipDefinition rel = schema.relationships().get(i);
                String path = "relationships[" + i + "]";
                if (isBlank(rel.type())) {
                    errors.add(path + ".type: must not be blank");
                } else if (!relationTypes.add(rel.type())) {
                    errors.add(path + ".type: duplicate type '" + rel.type() + "'");
                }
                if (isBlank(rel.from()) || !nodeLabels.contains(rel.from())) {
                    errors.add(path + ".from: unknown node label '" + rel.from() + "'");
                }
                if (isBlank(rel.to()) || !nodeLabels.contains(rel.to())) {
                    errors.add(path + ".to: unknown node label '" + rel.to() + "'");
                }
            }
        }

        if (schema.vectorIndexes() != null) {
            for (int i = 0; i < schema.vectorIndexes().size(); i++) {
                SchemaDocument.VectorIndexDefinition idx = schema.vectorIndexes().get(i);
                String path = "vectorIndexes[" + i + "]";
                if (idx.dimensions() == null || idx.dimensions() <= 0) {
                    errors.add(path + ".dimensions: must be > 0");
                }
                if (idx.similarity() != null) {
                    String sim = idx.similarity().toLowerCase(Locale.ROOT);
                    if (!sim.equals("cosine") && !sim.equals("euclidean") && !sim.equals("dot_product")) {
                        errors.add(path + ".similarity: unsupported value '" + idx.similarity() + "'");
                    }
                }
            }
        }
        return errors;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
