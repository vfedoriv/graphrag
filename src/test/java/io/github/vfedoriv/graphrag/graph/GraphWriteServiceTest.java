package io.github.vfedoriv.graphrag.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.data.neo4j.core.Neo4jClient;

class GraphWriteServiceTest {

    private final GraphWriteService service = new GraphWriteService(Mockito.mock(Neo4jClient.class, Answers.RETURNS_DEEP_STUBS));

    @Test
    void stableNodeId_isDeterministicAndResistantToDelimiterCollisions() throws Exception {
        Method stableNodeId = GraphWriteService.class.getDeclaredMethod("stableNodeId", String.class, String.class, List.class, Map.class);
        stableNodeId.setAccessible(true);

        String idA1 = (String) stableNodeId.invoke(service, "schema-1", "Person", List.of("a", "b"), Map.of("a", "1|2", "b", "3"));
        String idA2 = (String) stableNodeId.invoke(service, "schema-1", "Person", List.of("a", "b"), Map.of("a", "1|2", "b", "3"));
        String idB = (String) stableNodeId.invoke(service, "schema-1", "Person", List.of("a", "b"), Map.of("a", "1", "b", "2|3"));

        assertThat(idA1).isEqualTo(idA2);
        assertThat(idA1).isNotEqualTo(idB);
        assertThat(idA1).startsWith("node:");
    }

    @Test
    void stableRelationshipId_isDeterministic() throws Exception {
        Method stableRelationshipId = GraphWriteService.class.getDeclaredMethod(
            "stableRelationshipId",
            String.class,
            String.class,
            String.class,
            String.class
        );
        stableRelationshipId.setAccessible(true);

        String id1 = (String) stableRelationshipId.invoke(service, "schema-1", "KNOWS", "node:a", "node:b");
        String id2 = (String) stableRelationshipId.invoke(service, "schema-1", "KNOWS", "node:a", "node:b");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).startsWith("rel:");
    }

    @Test
    void stableNodeId_rejectsIncompleteIdentityMaterial() throws Exception {
        Method stableNodeId = GraphWriteService.class.getDeclaredMethod("stableNodeId", String.class, String.class, List.class, Map.class);
        stableNodeId.setAccessible(true);

        assertThatThrownBy(() -> stableNodeId.invoke(service, "schema-1", "Person", List.of("fullName", "birthDate"), Map.of("fullName", "Ada")))
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("Incomplete identity material for node label: Person.birthDate");
    }

    @Test
    void stableNodeId_rejectsMissingSchemaKeyDefinition() throws Exception {
        Method stableNodeId = GraphWriteService.class.getDeclaredMethod("stableNodeId", String.class, String.class, List.class, Map.class);
        stableNodeId.setAccessible(true);

        assertThatThrownBy(() -> stableNodeId.invoke(service, "schema-1", "Person", List.of(), Map.of("name", "Ada")))
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("Missing schema key definition for node label: Person");
    }

    @Test
    void filterDeclaredProperties_dropsUndeclaredNodeAndRelationshipProperties() throws Exception {
        Method filterDeclaredProperties = GraphWriteService.class.getDeclaredMethod(
            "filterDeclaredProperties",
            Map.class,
            Set.class,
            String.class,
            String.class
        );
        filterDeclaredProperties.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> nodeFiltered = (Map<String, Object>) filterDeclaredProperties.invoke(
            service,
            Map.of("contractId", "C-1", "undeclared", "x"),
            Set.of("contractId"),
            "node",
            "Contract"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> relFiltered = (Map<String, Object>) filterDeclaredProperties.invoke(
            service,
            Map.of("role", "supplier", "extra", "x"),
            Set.of("role"),
            "relationship",
            "HAS_PARTY"
        );

        assertThat(nodeFiltered).containsExactly(Map.entry("contractId", "C-1"));
        assertThat(relFiltered).containsExactly(Map.entry("role", "supplier"));
    }

    @Test
    void allowedRelationshipProperties_matchesExactRelationshipDefinition() throws Exception {
        Method allowedRelationshipProperties = GraphWriteService.class.getDeclaredMethod(
            "allowedRelationshipProperties",
            SchemaDocument.class,
            String.class,
            String.class,
            String.class
        );
        allowedRelationshipProperties.setAccessible(true);

        SchemaDocument schema = new SchemaDocument(
            "contracts",
            1,
            "desc",
            List.of(),
            List.of(
                new SchemaDocument.RelationshipDefinition(
                    "HAS_PARTY",
                    "Contract",
                    "Party",
                    "",
                    List.of(new SchemaDocument.PropertyDefinition("role", "string", false))
                )
            ),
            List.of(),
            List.of()
        );

        @SuppressWarnings("unchecked")
        Set<String> allowed = (Set<String>) allowedRelationshipProperties.invoke(service, schema, "HAS_PARTY", "Contract", "Party");
        assertThat(allowed).containsExactly("role");
    }
}
