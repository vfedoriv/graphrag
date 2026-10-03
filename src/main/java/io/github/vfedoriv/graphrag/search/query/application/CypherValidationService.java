package io.github.vfedoriv.graphrag.search.query.application;

import io.github.vfedoriv.graphrag.search.query.ports.QueryExecutor;
import io.github.vfedoriv.graphrag.search.query.domain.CypherLimitScanner;
import io.github.vfedoriv.graphrag.search.query.domain.CypherParsingSupport;
import io.github.vfedoriv.graphrag.search.query.domain.CypherSchemaSupport;
import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;

import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.search.query.api.error.QueryDeadlineExceededException;
import io.github.vfedoriv.graphrag.search.query.domain.QueryValidationResult;
import io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CypherValidationService {

    private static final Set<String> INFRA_LABELS = Set.of(
        "KnowledgeBase", "SchemaDefinition", "DocumentUpload", "DocumentChunk", "ExtractionRun", "DocumentProcessingRun",
        "ExtractedEntity", "ExtractedRelation"
    );
    private final RuntimeSettingsAccess runtimeSettingsService;
    private final SearchSchemas activeSchemaResolver;
    private final QueryExecutor queryNeo4jExecutor;

    public CypherValidationService(
        RuntimeSettingsAccess runtimeSettingsService,
        SearchSchemas activeSchemaResolver,
        QueryExecutor queryNeo4jExecutor
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.activeSchemaResolver = activeSchemaResolver;
        this.queryNeo4jExecutor = queryNeo4jExecutor;
    }

    public QueryValidationResult validate(String knowledgeBaseId, String cypher, Map<String, Object> parameters) {
        return validate(knowledgeBaseId, cypher, parameters, io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy.from(runtimeSettingsService.query()));
    }

    public QueryValidationResult validate(
        String knowledgeBaseId,
        String cypher,
        Map<String, Object> parameters,
        QueryPolicy policy
    ) {
        log.info(
            "Validating Cypher for knowledge base: knowledgeBaseId={}, cypherLength={}, parameterCount={}",
            knowledgeBaseId,
            LogMetadata.length(cypher),
            parameters == null ? 0 : parameters.size()
        );
        SchemaSnapshot schemaContext = activeSchemaResolver.resolveActive(knowledgeBaseId);
        QueryValidationResult result = validate(schemaContext.schema(), cypher, parameters, policy);
        log.info(
            "Cypher validation completed for knowledge base: knowledgeBaseId={}, valid={}, errorCount={}",
            knowledgeBaseId,
            result.valid(),
            result.errors().size()
        );
        return result;
    }

    public QueryValidationResult validate(SchemaDocument schema, String cypher, Map<String, Object> parameters) {
        return validate(schema, cypher, parameters, io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy.from(runtimeSettingsService.query()));
    }

    public QueryValidationResult validate(SchemaDocument schema, String cypher, Map<String, Object> parameters, QueryPolicy policy) {
        long startNanos = System.nanoTime();
        List<String> errors = new ArrayList<>();
        String normalizedCypher = cypher == null ? "" : cypher.trim();
        Map<String, Object> normalizedParameters = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);

        rejectBlockedKeywords(normalizedCypher, policy, errors);
        validateSchemaReferences(schema, normalizedCypher, errors);
        normalizedCypher = enforceLimit(normalizedCypher, normalizedParameters, policy, errors);

        if (errors.isEmpty()) {
            explain(normalizedCypher, normalizedParameters, policy, errors);
        }

        QueryValidationResult result = new QueryValidationResult(
            errors.isEmpty(),
            normalizedCypher,
            Map.copyOf(normalizedParameters),
            List.copyOf(errors),
            policy
        );
        log.info(
            "Cypher validation completed: schemaName={}, valid={}, errorCount={}, cypherLength={}, elapsedMs={}",
            schema.name(),
            result.valid(),
            result.errors().size(),
            LogMetadata.length(result.cypher()),
            LogMetadata.elapsedMillis(startNanos)
        );
        return result;
    }

    private void rejectBlockedKeywords(String cypher, QueryPolicy policy, List<String> errors) {
        String upper = cypher.toUpperCase(Locale.ROOT);
        for (String keyword : policy.blockedKeywords()) {
            if (upper.contains(keyword.toUpperCase(Locale.ROOT))) {
                errors.add("Blocked keyword detected: " + keyword);
            }
        }
    }

    private String enforceLimit(
        String cypher,
        Map<String, Object> parameters,
        QueryPolicy policy,
        List<String> errors
    ) {
        List<CypherLimitScanner.LimitClause> limits = CypherLimitScanner.topLevelLimits(cypher);
        if (limits.isEmpty()) {
            if (!policy.requireLimit()) {
                return cypher;
            }
            if (parameters.containsKey("__limit")) {
                errors.add("Reserved limit parameter is already supplied: __limit");
                return cypher;
            }
            parameters.put("__limit", policy.maxRows());
            return cypher + "\nLIMIT $__limit";
        }
        for (CypherLimitScanner.LimitClause limit : limits) {
            if (!limit.supported()) {
                errors.add("LIMIT must use a numeric literal or named parameter");
                continue;
            }
            if (limit.literal() != null && limit.literal() > policy.maxRows()) {
                errors.add("LIMIT exceeds configured maximum rows: " + policy.maxRows());
                continue;
            }
            if (limit.parameter() != null) {
                validateBoundLimit(limit.parameter(), parameters, policy, errors);
            }
        }
        return cypher;
    }

    private void validateBoundLimit(
        String parameter,
        Map<String, Object> parameters,
        QueryPolicy policy,
        List<String> errors
    ) {
        Object value = parameters.get(parameter);
        if (!(value instanceof Number number)) {
            errors.add("LIMIT parameter must be a numeric value: " + parameter);
            return;
        }
        double numericValue = number.doubleValue();
        if (!Double.isFinite(numericValue) || numericValue < 0 || Math.floor(numericValue) != numericValue) {
            errors.add("LIMIT parameter must be a non-negative integer: " + parameter);
            return;
        }
        if (numericValue > policy.maxRows()) {
            errors.add("LIMIT exceeds configured maximum rows: " + policy.maxRows());
        }
    }

    private void validateSchemaReferences(SchemaDocument schema, String cypher, List<String> errors) {
        Set<String> allowedLabels = CypherSchemaSupport.allowedLabels(schema, INFRA_LABELS);
        Set<String> allowedRelationshipTypes = CypherSchemaSupport.allowedRelationshipTypes(schema);
        Set<String> allowedProperties = CypherSchemaSupport.allowedProperties(schema);

        CypherParsingSupport.extractNodeLabels(cypher).stream()
            .filter(label -> !allowedLabels.contains(label))
            .forEach(label -> errors.add("Unknown label: " + label));
        CypherParsingSupport.extractRelationshipTypes(cypher).stream()
            .filter(type -> !allowedRelationshipTypes.contains(type))
            .forEach(type -> errors.add("Unknown relationship type: " + type));
        CypherParsingSupport.extractPropertyReferences(cypher).stream()
            .filter(property -> !allowedProperties.contains(property))
            .forEach(property -> errors.add("Unknown property: " + property));
    }

    private void explain(String cypher, Map<String, Object> params, QueryPolicy policy, List<String> errors) {
        try {
            queryNeo4jExecutor.explain(cypher, params, policy);
        } catch (QueryDeadlineExceededException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cypher EXPLAIN failed: cypherLength={}, parameterCount={}, exceptionType={}", cypher.length(), params.size(), ex.getClass().getSimpleName());
            errors.add("Cypher syntax or planner validation failed");
        }
    }
}
