package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class CypherValidationService {

    private static final Pattern LABEL_PATTERN = Pattern.compile(":[`]?([A-Za-z_][A-Za-z0-9_]*)[`]?");
    private static final Pattern REL_PATTERN = Pattern.compile("\\[:[`]?([A-Za-z_][A-Za-z0-9_]*)[`]?");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\.([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> INFRA_LABELS = Set.of(
        "KnowledgeBase", "SchemaDefinition", "DocumentUpload", "DocumentChunk", "ExtractionRun", "ExtractedEntity", "ExtractedRelation"
    );
    private final AppProperties appProperties;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final SchemaParser schemaParser;
    private final Neo4jClient neo4jClient;

    public CypherValidationService(
        AppProperties appProperties,
        KnowledgeBaseRepository knowledgeBaseRepository,
        SchemaDefinitionRepository schemaDefinitionRepository,
        SchemaParser schemaParser,
        Neo4jClient neo4jClient
    ) {
        this.appProperties = appProperties;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.schemaParser = schemaParser;
        this.neo4jClient = neo4jClient;
    }

    public QueryValidationResult validate(String knowledgeBaseId, String cypher, Map<String, Object> parameters) {
        KnowledgeBaseNode kb = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
        if (kb.getActiveSchemaId() == null || kb.getActiveSchemaId().isBlank()) {
            throw new IllegalStateException("No active schema for knowledge base: " + knowledgeBaseId);
        }
        var schemaNode = schemaDefinitionRepository.findById(kb.getActiveSchemaId())
            .orElseThrow(() -> new NotFoundException("Schema not found: " + kb.getActiveSchemaId()));
        return validate(schemaParser.parse(schemaNode.getContent()), cypher, parameters);
    }

    QueryValidationResult validate(SchemaDocument schema, String cypher, Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        String normalizedCypher = cypher == null ? "" : cypher.trim();
        Map<String, Object> normalizedParameters = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);

        rejectBlockedKeywords(normalizedCypher, errors);
        validateSchemaReferences(schema, normalizedCypher, errors);

        if (appProperties.query().requireLimit() && !LIMIT_PATTERN.matcher(normalizedCypher).find()) {
            normalizedCypher = normalizedCypher + "\nLIMIT $__limit";
            normalizedParameters.put("__limit", appProperties.query().maxRows());
        }

        if (errors.isEmpty()) {
            explain(normalizedCypher, normalizedParameters, errors);
        }

        return new QueryValidationResult(errors.isEmpty(), normalizedCypher, Map.copyOf(normalizedParameters), List.copyOf(errors));
    }

    private void rejectBlockedKeywords(String cypher, List<String> errors) {
        String upper = cypher.toUpperCase(Locale.ROOT);
        for (String keyword : appProperties.query().blockedKeywords()) {
            if (upper.contains(keyword.toUpperCase(Locale.ROOT))) {
                errors.add("Blocked keyword detected: " + keyword);
            }
        }
    }

    private void validateSchemaReferences(SchemaDocument schema, String cypher, List<String> errors) {
        Set<String> allowedLabels = new HashSet<>(INFRA_LABELS);
        for (var node : schema.nodes()) {
            allowedLabels.add(node.label());
        }
        Set<String> allowedRelationshipTypes = new HashSet<>();
        for (var relationship : schema.relationships()) {
            allowedRelationshipTypes.add(relationship.type());
        }
        Set<String> allowedProperties = new HashSet<>(Set.of(
            "id", "sourceDocumentId", "sourceChunkIds", "schemaId", "extractionRunId", "confidence", "createdAt"
        ));
        for (var node : schema.nodes()) {
            for (var property : node.properties()) {
                allowedProperties.add(property.name());
            }
        }
        for (var relationship : schema.relationships()) {
            for (var property : relationship.properties()) {
                allowedProperties.add(property.name());
            }
        }

        matchCaptures(LABEL_PATTERN, cypher).stream()
            .filter(label -> !allowedLabels.contains(label))
            .forEach(label -> errors.add("Unknown label: " + label));
        matchCaptures(REL_PATTERN, cypher).stream()
            .filter(type -> !allowedRelationshipTypes.contains(type))
            .forEach(type -> errors.add("Unknown relationship type: " + type));
        matchCaptures(PROPERTY_PATTERN, cypher).stream()
            .filter(property -> !allowedProperties.contains(property))
            .forEach(property -> errors.add("Unknown property: " + property));
    }

    private List<String> matchCaptures(Pattern pattern, String cypher) {
        Matcher matcher = pattern.matcher(cypher);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private void explain(String cypher, Map<String, Object> params, List<String> errors) {
        try {
            neo4jClient.query("EXPLAIN " + cypher).bindAll(params).fetch().all();
        } catch (Exception ex) {
            errors.add("Cypher syntax or planner validation failed: " + ex.getMessage());
        }
    }
}
