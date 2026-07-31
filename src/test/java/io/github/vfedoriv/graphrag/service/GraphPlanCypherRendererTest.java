package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Aggregation;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.AggregationFunction;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ComparisonFilter;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ComparisonOperator;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Direction;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Ordering;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyReference;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.SortDirection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.StringLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.TypedHop;
import io.github.vfedoriv.graphrag.repository.GraphRetrievalRepository.Query;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidatedGraphPlan;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphPlanCypherRendererTest {

    private final GraphPlanCypherRenderer renderer = new GraphPlanCypherRenderer();

    @Test
    void rendersOnlyTemplateOwnedIdentifiersAndParameterizedLiterals() {
        GraphPlan plan = plan(null);

        Query query = renderer.render(validated(plan));

        assertThat(query.cypher())
            .contains("MATCH path = (n0:`Person`)-[r0:`WORKS_AT`]->(n1:`Company`)")
            .contains("evidenceId")
            .contains(".knowledgeBaseId = $knowledgeBaseId")
            .contains(".schemaId = $schemaId")
            .contains(".kind = 'PARENT'")
            .contains("LIMIT $limit")
            .doesNotContain("Alice");
        assertThat(query.parameters()).containsEntry("knowledgeBaseId", "kb-1")
            .containsEntry("schemaId", "schema-1")
            .containsValue("Alice");
    }

    @Test
    void rendersBoundedAggregationAndOrderingFromClosedVariants() {
        Aggregation aggregation = new Aggregation(
            AggregationFunction.COUNT_DISTINCT,
            new PropertyReference(0, "name"),
            "people"
        );

        Query query = renderer.render(validated(plan(aggregation)));

        assertThat(query.cypher())
            .contains("LIMIT $scanLimit")
            .contains("count(DISTINCT n0.`name`) AS `people`")
            .contains("ORDER BY `people` DESC")
            .contains("LIMIT $limit");
    }

    private GraphPlan plan(Aggregation aggregation) {
        return new GraphPlan(
            "Person",
            List.of(new ComparisonFilter(
                new PropertyReference(0, "name"), ComparisonOperator.EQUALS, new StringLiteral("Alice")
            )),
            List.of(new TypedHop("WORKS_AT", "Company", Direction.OUTGOING)),
            List.of(new PropertyProjection(new PropertyReference(1, "name"), "company")),
            aggregation,
            List.of(new Ordering(aggregation == null ? "company" : "people", SortDirection.DESCENDING)),
            5
        );
    }

    private ValidatedGraphPlan validated(GraphPlan plan) {
        return new ValidatedGraphPlan(
            plan,
            new ActiveSchemaContext("kb-1", "schema-1", null, null),
            50,
            Duration.ofSeconds(3)
        );
    }
}
