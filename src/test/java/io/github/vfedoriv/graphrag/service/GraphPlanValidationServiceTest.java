package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Aggregation;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.AggregationFunction;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ComparisonFilter;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ComparisonOperator;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Direction;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.LongLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.NodeProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Ordering;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyReference;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.SortDirection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.StringLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.TypedHop;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphPlanValidationServiceTest {

    private ActiveSchemaResolver activeSchemaResolver;
    private RuntimeSettingsService runtimeSettingsService;
    private GraphPlanValidationService validator;

    @BeforeEach
    void setUp() {
        activeSchemaResolver = mock(ActiveSchemaResolver.class);
        runtimeSettingsService = mock(RuntimeSettingsService.class);
        RuntimeSettingsService.QuerySettings querySettings = new RuntimeSettingsService.QuerySettings(
            10, 5, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, false,
            true, 4096, 8, 20, 2, 1
        );
        when(runtimeSettingsService.query()).thenReturn(querySettings);
        when(activeSchemaResolver.resolve("kb-1"))
            .thenReturn(new ActiveSchemaContext("kb-1", "schema-1", null, schema()));
        validator = new GraphPlanValidationService(activeSchemaResolver, runtimeSettingsService);
    }

    @Test
    void acceptsSchemaConstrainedTwoHopPlan() {
        GraphPlanValidationService.ValidationResult result = validator.validate("kb-1", validPlan());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.validatedPlan().maxRows()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidIdentifiersAndUnknownRelationships() {
        GraphPlan invalidRoot = copy(validPlan(), "Person`) MATCH (n)", validPlan().filters(), validPlan().hops(),
            validPlan().projections(), validPlan().aggregation(), validPlan().ordering(), 2);
        GraphPlan invalidRelationship = copy(validPlan(), "Person", validPlan().filters(),
            List.of(new TypedHop("UNKNOWN", "Company", Direction.OUTGOING)),
            validPlan().projections(), null, validPlan().ordering(), 2);

        assertThat(validator.validate("kb-1", invalidRoot).errors()).contains("root-label.unknown");
        assertThat(validator.validate("kb-1", invalidRelationship).errors()).contains("hop.schema-mismatch");
    }

    @Test
    void rejectsOperatorsAndLiteralsThatDoNotMatchSchemaTypes() {
        GraphPlan invalidOperator = copy(validPlan(), "Person", List.of(new ComparisonFilter(
            new PropertyReference(0, "name"), ComparisonOperator.GREATER_THAN, new StringLiteral("A")
        )), validPlan().hops(), validPlan().projections(), null, validPlan().ordering(), 2);
        GraphPlan invalidLiteral = copy(validPlan(), "Person", List.of(new ComparisonFilter(
            new PropertyReference(0, "age"), ComparisonOperator.EQUALS, new StringLiteral("forty")
        )), validPlan().hops(), validPlan().projections(), null, validPlan().ordering(), 2);

        assertThat(validator.validate("kb-1", invalidOperator).errors()).contains("filter.operator-type-mismatch");
        assertThat(validator.validate("kb-1", invalidLiteral).errors()).contains("filter.literal-type-mismatch");
    }

    @Test
    void rejectsUnknownProjectionsOrderingAndInvalidAggregationTypes() {
        GraphPlan invalid = copy(validPlan(), "Person", validPlan().filters(), validPlan().hops(),
            List.of(new PropertyProjection(new PropertyReference(2, "unknown"), "value")),
            new Aggregation(AggregationFunction.SUM, new PropertyReference(0, "name"), "total"),
            List.of(new Ordering("missing", SortDirection.ASCENDING)), 2);

        assertThat(validator.validate("kb-1", invalid).errors())
            .contains("projection.property-unknown", "aggregation.property-type-mismatch", "ordering.projection-unknown");
    }

    @Test
    void rejectsRuntimeRowLimitAndMoreThanTwoHops() {
        List<TypedHop> threeHops = List.of(
            new TypedHop("WORKS_AT", "Company", Direction.OUTGOING),
            new TypedHop("LOCATED_IN", "City", Direction.OUTGOING),
            new TypedHop("LOCATED_IN", "City", Direction.OUTGOING)
        );
        GraphPlan invalid = copy(validPlan(), "Person", validPlan().filters(), threeHops,
            validPlan().projections(), null, validPlan().ordering(), 11);

        assertThat(validator.validate("kb-1", invalid).errors())
            .contains("hops.limit-exceeded", "limit.out-of-range");
    }

    private GraphPlan validPlan() {
        return new GraphPlan(
            "Person",
            List.of(new ComparisonFilter(
                new PropertyReference(0, "age"), ComparisonOperator.GREATER_THAN_OR_EQUAL, new LongLiteral(18L)
            )),
            List.of(
                new TypedHop("WORKS_AT", "Company", Direction.OUTGOING),
                new TypedHop("LOCATED_IN", "City", Direction.OUTGOING)
            ),
            List.of(
                new NodeProjection(0, "person"),
                new PropertyProjection(new PropertyReference(2, "name"), "city")
            ),
            null,
            List.of(new Ordering("city", SortDirection.ASCENDING)),
            2
        );
    }

    private GraphPlan copy(
        GraphPlan ignored,
        String root,
        List<io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Filter> filters,
        List<TypedHop> hops,
        List<io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Projection> projections,
        Aggregation aggregation,
        List<Ordering> ordering,
        int limit
    ) {
        return new GraphPlan(root, filters, hops, projections, aggregation, ordering, limit);
    }

    private SchemaDocument schema() {
        return new SchemaDocument(
            "people",
            1,
            null,
            List.of(
                new SchemaDocument.NodeDefinition("Person", null, List.of("name"), List.of(
                    new SchemaDocument.PropertyDefinition("name", "string", true),
                    new SchemaDocument.PropertyDefinition("age", "integer", false)
                )),
                new SchemaDocument.NodeDefinition("Company", null, List.of("name"), List.of(
                    new SchemaDocument.PropertyDefinition("name", "string", true)
                )),
                new SchemaDocument.NodeDefinition("City", null, List.of("name"), List.of(
                    new SchemaDocument.PropertyDefinition("name", "string", true)
                ))
            ),
            List.of(
                new SchemaDocument.RelationshipDefinition("WORKS_AT", "Person", "Company", null, List.of()),
                new SchemaDocument.RelationshipDefinition("LOCATED_IN", "Company", "City", null, List.of())
            ),
            List.of(),
            List.of()
        );
    }
}
