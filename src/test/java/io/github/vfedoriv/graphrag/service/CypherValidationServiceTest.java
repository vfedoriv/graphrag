package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CypherValidationServiceTest {

    @Mock
    private QueryNeo4jExecutor queryNeo4jExecutor;

    @Test
    void rejectsUnsafeKeyword() {
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) DELETE n", Map.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Blocked keyword"));
    }

    @Test
    void rejectsUnknownSchemaReferences() {
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:Unknown)-[:BAD]->(m:Contract) RETURN n.foo", Map.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Unknown label"));
        assertThat(result.errors()).anyMatch(e -> e.contains("Unknown relationship type"));
        assertThat(result.errors()).anyMatch(e -> e.contains("Unknown property"));
    }

    @Test
    void acceptsRelationshipUnionWithoutTreatingTypesAsLabels() {
        stubExplain();
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), """
            MATCH (c:Component)-[r:HAS_GREASE_RECOMMENDATION|REQUIRES_GREASE]->(m:Material)
            RETURN c.id AS componentId,
                   m.composition AS greaseComposition,
                   m.miscibilityNote AS miscibilityNote,
                   type(r) AS recommendationType
            LIMIT 200
            """, Map.of());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).noneMatch(e -> e.contains("Unknown label: HAS_GREASE_RECOMMENDATION"));
        assertThat(result.errors()).noneMatch(e -> e.contains("Unknown label: REQUIRES_GREASE"));
    }

    @Test
    void rejectsUnknownRelationshipUnionMembersOnlyAsRelationships() {
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(
            schema(),
            "MATCH (c:Component)-[r:HAS_GREASE_RECOMMENDATION|UNKNOWN_GREASE]->(m:Material) RETURN type(r)",
            Map.of()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Unknown relationship type: UNKNOWN_GREASE");
        assertThat(result.errors()).noneMatch(e -> e.contains("Unknown label: HAS_GREASE_RECOMMENDATION"));
        assertThat(result.errors()).noneMatch(e -> e.contains("Unknown label: UNKNOWN_GREASE"));
    }

    @Test
    void acceptsAliasedRelationshipUnion() {
        stubExplain();
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(
            schema(),
            "MATCH (c:Component)<-[r:HAS_GREASE_RECOMMENDATION|REQUIRES_GREASE]-(m:Material) RETURN type(r) LIMIT 10",
            Map.of()
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validatesBacktickQuotedNodeLabels() {
        stubExplain();
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:`Contract`) RETURN n.contractId LIMIT 10", Map.of());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsUnknownBacktickQuotedNodeLabel() {
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:`Unknown`) RETURN n.contractId LIMIT 10", Map.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Unknown label: Unknown");
    }

    @Test
    void acceptsValidQualifiedPropertiesAndRejectsUnknownOnes() {
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(
            schema(),
            "MATCH (c:Contract) RETURN c.contractId, c.unexpectedProperty LIMIT 10",
            Map.of()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Unknown property: unexpectedProperty");
        assertThat(result.errors()).noneMatch(e -> e.contains("Unknown property: contractId"));
    }

    @Test
    void validatesMixedQueryShapes() {
        stubExplain();
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), """
            MATCH (c:Component {id: $componentId})<-[r:REQUIRES_GREASE]-(m:Material)
            MATCH (contract:Contract)-[party:HAS_PARTY]->(other:Contract)
            RETURN c.id, m.composition, contract.title, type(party), type(r)
            LIMIT 20
            """, Map.of("componentId", "C-1"));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void injectsLimitWhenMissing() {
        stubExplain();
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) RETURN n", Map.of());
        assertThat(result.cypher()).contains("LIMIT $__limit");
        assertThat(result.parameters()).containsEntry("__limit", 200);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void keepsLimitWhenAlreadyPresent() {
        stubExplain();
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) RETURN n LIMIT 5", Map.of());
        assertThat(result.cypher()).doesNotContain("$__limit");
    }

    @Test
    void rejectsLiteralLimitAboveRuntimePolicy() {
        CypherValidationService service = service();

        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) RETURN n LIMIT 201", Map.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("LIMIT exceeds configured maximum rows: 200");
    }

    @Test
    void rejectsBoundLimitAboveRuntimePolicy() {
        CypherValidationService service = service();

        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) RETURN n LIMIT $requested", Map.of("requested", 201));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("LIMIT exceeds configured maximum rows: 200");
    }

    @Test
    void ignoresLimitTextInLiteralsAndCommentsBeforeInjectingLimit() {
        stubExplain();
        CypherValidationService service = service();

        QueryValidationResult result = service.validate(
            schema(),
            "MATCH (n:Contract) WHERE n.title = 'LIMIT 1000' // LIMIT 1000\nRETURN n",
            Map.of()
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.cypher()).endsWith("LIMIT $__limit");
        assertThat(result.parameters()).containsEntry("__limit", 200);
    }

    @Test
    void preservesBoundLimitWithinRuntimePolicy() {
        stubExplain();
        CypherValidationService service = service();

        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) RETURN n LIMIT $requested", Map.of("requested", 25));

        assertThat(result.valid()).isTrue();
        assertThat(result.cypher()).doesNotContain("$__limit");
    }

    @Test
    void acceptsBoundLimitParameterNamedLimit() {
        stubExplain();
        CypherValidationService service = service();

        QueryValidationResult result = service.validate(
            schema(),
            "MATCH (n:Contract) RETURN n LIMIT $limit",
            Map.of("limit", 25)
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void resolvesActiveSchemaForKnowledgeBaseValidation() {
        stubExplain();
        ActiveSchemaResolver resolver = org.mockito.Mockito.mock(ActiveSchemaResolver.class);
        SchemaDefinitionNode schemaDefinition = new SchemaDefinitionNode();
        schemaDefinition.setId("schema-1");
        schemaDefinition.setName("contracts");
        when(resolver.resolve("kb-1")).thenReturn(new ActiveSchemaContext("kb-1", "schema-1", schemaDefinition, schema()));

        QueryValidationResult result = service(resolver).validate("kb-1", "MATCH (n:Contract) RETURN n LIMIT 5", Map.of());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void marksInvalidWhenExplainFails() {
        doThrow(new IllegalArgumentException("Invalid input"))
            .when(queryNeo4jExecutor).explain(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.any()
            );
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) RETURN n", Map.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("planner validation failed"));
    }

    private CypherValidationService service() {
        return service(org.mockito.Mockito.mock(ActiveSchemaResolver.class));
    }

    private CypherValidationService service(ActiveSchemaResolver activeSchemaResolver) {
        return new CypherValidationService(
            io.github.vfedoriv.graphrag.TestRuntimeSettings.from(new AppProperties(
                new AppProperties.Neo4j("neo4j"),
                new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
                new AppProperties.Storage(Path.of("var/documents")),
                new AppProperties.Chunking(800, 80, 4000),
                new AppProperties.Query(200, 15, true, List.of("CREATE", "MERGE", "DELETE")),
                new AppProperties.Extraction(40, 80, 2)
            )),
            activeSchemaResolver,
            queryNeo4jExecutor
        );
    }

    private void stubExplain() {
        org.mockito.Mockito.doNothing().when(queryNeo4jExecutor).explain(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private SchemaDocument schema() {
        String json = """
            {
              "name": "contracts",
              "version": 1,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [
                    {"name": "contractId", "type": "string"},
                    {"name": "title", "type": "string"}
                  ]
                },
                {
                  "label": "Component",
                  "key": "id",
                  "properties": [
                    {"name": "id", "type": "string"}
                  ]
                },
                {
                  "label": "Material",
                  "key": "id",
                  "properties": [
                    {"name": "id", "type": "string"},
                    {"name": "composition", "type": "string"},
                    {"name": "miscibilityNote", "type": "string"}
                  ]
                }
              ],
              "relationships": [
                {
                  "type": "HAS_PARTY",
                  "from": "Contract",
                  "to": "Contract",
                  "properties": [
                    {"name": "role", "type": "string"}
                  ]
                },
                {
                  "type": "HAS_GREASE_RECOMMENDATION",
                  "from": "Component",
                  "to": "Material",
                  "properties": []
                },
                {
                  "type": "REQUIRES_GREASE",
                  "from": "Component",
                  "to": "Material",
                  "properties": []
                }
              ]
            }
            """;
        return new SchemaParser().parse(json);
    }
}
