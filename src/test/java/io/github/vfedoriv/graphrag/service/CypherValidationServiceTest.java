package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

@ExtendWith(MockitoExtension.class)
class CypherValidationServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

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
    void marksInvalidWhenExplainFails() {
        doThrow(new IllegalArgumentException("Invalid input"))
            .when(neo4jClient).query(org.mockito.ArgumentMatchers.startsWith("EXPLAIN "));
        CypherValidationService service = service();
        QueryValidationResult result = service.validate(schema(), "MATCH (n:Contract) RETURN n", Map.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("planner validation failed"));
    }

    private CypherValidationService service() {
        return new CypherValidationService(
            new AppProperties(
                new AppProperties.Neo4j("neo4j"),
                new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
                new AppProperties.Storage(Path.of("var/documents")),
                new AppProperties.Chunking(800, 80, 4000),
                new AppProperties.Query(200, 15, true, List.of("CREATE", "MERGE", "DELETE")),
                new AppProperties.Extraction(40, 80, 2)
            ),
            org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository.class),
            org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository.class),
            new SchemaParser(),
            neo4jClient
        );
    }

    private void stubExplain() {
        when(neo4jClient.query(org.mockito.ArgumentMatchers.anyString()).bindAll(org.mockito.ArgumentMatchers.anyMap()).fetch().all())
            .thenReturn(List.of());
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
