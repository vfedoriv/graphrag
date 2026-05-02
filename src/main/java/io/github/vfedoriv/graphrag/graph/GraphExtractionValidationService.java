package io.github.vfedoriv.graphrag.graph;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GraphExtractionValidationService {

    private final AppProperties appProperties;

    public GraphExtractionValidationService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void validate(GraphExtractionResult result, SchemaDocument schema) {
        if (result == null) {
            throw new IllegalArgumentException("Extraction payload must not be null");
        }
        Map<String, SchemaDocument.NodeDefinition> nodeDefs = new HashMap<>();
        for (var node : schema.nodes()) {
            nodeDefs.put(node.label(), node);
        }
        Set<String> relDefs = new HashSet<>();
        for (var rel : schema.relationships()) {
            relDefs.add(rel.type() + "|" + rel.from() + "|" + rel.to());
        }

        if (result.nodes() != null && result.nodes().size() > appProperties.extraction().maxEntitiesPerChunk()) {
            throw new IllegalArgumentException("Too many extracted entities for chunk");
        }
        if (result.relationships() != null
            && result.relationships().size() > appProperties.extraction().maxRelationshipsPerChunk()) {
            throw new IllegalArgumentException("Too many extracted relationships for chunk");
        }

        if (result.nodes() != null) {
            for (var extracted : result.nodes()) {
                SchemaDocument.NodeDefinition nodeDef = nodeDefs.get(extracted.label());
                if (nodeDef == null) {
                    throw new IllegalArgumentException("Unknown node label: " + extracted.label());
                }
                Object keyValue = extracted.properties() == null ? null : extracted.properties().get(nodeDef.key());
                if (keyValue == null || keyValue.toString().isBlank()) {
                    throw new IllegalArgumentException("Node key is missing: " + extracted.label() + "." + nodeDef.key());
                }
            }
        }

        if (result.relationships() != null) {
            for (var extracted : result.relationships()) {
                String ruleKey = extracted.type() + "|" + extracted.fromLabel() + "|" + extracted.toLabel();
                if (!relDefs.contains(ruleKey)) {
                    throw new IllegalArgumentException("Unknown relationship rule: " + ruleKey);
                }
                if (extracted.fromKey() == null || extracted.fromKey().isEmpty()
                    || extracted.toKey() == null || extracted.toKey().isEmpty()) {
                    throw new IllegalArgumentException("Relationship endpoint keys must not be empty");
                }
            }
        }
    }
}
