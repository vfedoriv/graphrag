package io.github.vfedoriv.graphrag.service;
import io.github.vfedoriv.graphrag.search.retrieval.application.validation.GraphPlanValidationService;

import io.github.vfedoriv.graphrag.search.retrieval.domain.GraphPlanCypherRenderer;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Aggregation;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.AggregationFunction;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ComparisonFilter;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ComparisonOperator;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Direction;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Ordering;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.PropertyProjection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.PropertyReference;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.SortDirection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.StringLiteral;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.TypedHop;
import io.github.vfedoriv.graphrag.search.retrieval.domain.GraphQuery;
import io.github.vfedoriv.graphrag.search.retrieval.domain.GraphPlanValidation.ValidatedGraphPlan;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphPlanCypherRendererTest {

    private final GraphPlanCypherRenderer renderer = new GraphPlanCypherRenderer();

    @Test
    void rendersOnlyTemplateOwnedIdentifiersAndParameterizedLiterals() {
        GraphPlan plan = plan(null);

        GraphQuery query = renderer.render(validated(plan));

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

        GraphQuery query = renderer.render(validated(plan(aggregation)));

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
            new SchemaSnapshot("kb-1", "schema-1", null, 0, null, null, null, null, null, null, null, null),
            50,
            Duration.ofSeconds(3)
        );
    }
}
