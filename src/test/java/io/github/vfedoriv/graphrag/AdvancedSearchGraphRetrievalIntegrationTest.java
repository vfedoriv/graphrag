package io.github.vfedoriv.graphrag;

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
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Ordering;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyReference;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.SortDirection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.TypedHop;
import io.github.vfedoriv.graphrag.repository.GraphRetrievalRepository;
import io.github.vfedoriv.graphrag.schemas.registry.application.ActiveSchemaContext;
import io.github.vfedoriv.graphrag.service.AdvancedSearchGraphRetriever;
import io.github.vfedoriv.graphrag.service.GraphPlanCypherRenderer;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidatedGraphPlan;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@IntegrationTest
class AdvancedSearchGraphRetrievalIntegrationTest {

    @Autowired private Neo4jClient neo4jClient;
    @Autowired private GraphPlanCypherRenderer renderer;
    @Autowired private GraphRetrievalRepository repository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        IntegrationTestLifecycle.reset(jdbcTemplate, neo4jClient);
        createPath("alice", "kb-1", "Alice", 42, "Acme", "Kyiv", "run-alice", "run-alice", 0.40);
        createPath("bob", "kb-1", "Bob", 35, "Beta", "Kyiv", "run-bob", "run-bob", 0.50);
        createPath("other", "kb-2", "Zoe", 60, "Other", "Zurich", "run-other", "run-other", 0.99);
        createPath("stale", "kb-1", "Yara", 55, "Stale", "Yerevan", "run-current", "run-stale", 0.95);
        createUnsupportedPathWithoutEvidence();
    }

    @Test
    void filtersAndOrdersTwoHopRowsAfterKbAndParentScopeBeforeApplyingLimit() {
        GraphPlan plan = new GraphPlan(
            "Person",
            List.of(new ComparisonFilter(
                new PropertyReference(0, "age"), ComparisonOperator.GREATER_THAN_OR_EQUAL, new LongLiteral(30L)
            )),
            hops(),
            List.of(
                new PropertyProjection(new PropertyReference(0, "name"), "person"),
                new PropertyProjection(new PropertyReference(2, "name"), "city")
            ),
            null,
            List.of(new Ordering("person", SortDirection.ASCENDING)),
            1
        );

        Result result = retriever(plan, 20).retrieve(request(plan));

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().values()).containsEntry("person", "Alice").containsEntry("city", "Kyiv");
        assertThat(result.rows().getFirst().facts()).hasSize(5);
        assertThat(result.rows().getFirst().facts())
            .allSatisfy(fact -> assertThat(fact.citations())
                .allSatisfy(citation -> assertThat(citation.chunkId()).startsWith("parent-alice")));
    }

    @Test
    void aggregatesScopedPathsAndOrdersBoundedGroups() {
        GraphPlan plan = new GraphPlan(
            "Person",
            List.of(),
            hops(),
            List.of(new PropertyProjection(new PropertyReference(2, "name"), "city")),
            new Aggregation(AggregationFunction.COUNT, null, "people"),
            List.of(new Ordering("people", SortDirection.DESCENDING)),
            2
        );

        Result result = retriever(plan, 20).retrieve(request(plan));

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().values()).containsEntry("city", "Kyiv").containsEntry("people", 2L);
        assertThat(result.rows().getFirst().facts()).hasSize(10);
    }

    @Test
    void excludesStrongerCrossKbEvidenceMissingProvenanceAndStaleParents() {
        GraphPlan plan = new GraphPlan(
            "Person",
            List.of(),
            hops(),
            List.of(new PropertyProjection(new PropertyReference(0, "name"), "person")),
            null,
            List.of(new Ordering("person", SortDirection.ASCENDING)),
            20
        );

        Result result = retriever(plan, 20).retrieve(request(plan));

        assertThat(result.rows()).extracting(row -> row.values().get("person"))
            .containsExactly("Alice", "Bob")
            .doesNotContain("Zoe", "Yara", "Xavier");
    }

    private AdvancedSearchGraphRetriever retriever(GraphPlan plan, int maxRows) {
        GraphPlanValidationService validation = mock(GraphPlanValidationService.class);
        ValidatedGraphPlan validated = new ValidatedGraphPlan(
            plan,
            new ActiveSchemaContext("kb-1", "schema-1", null, null),
            maxRows,
            Duration.ofSeconds(10)
        );
        when(validation.validate("kb-1", plan)).thenReturn(new ValidationResult(true, List.of(), validated));
        return new AdvancedSearchGraphRetriever(validation, renderer, repository);
    }

    private Request request(GraphPlan plan) {
        return new Request("kb-1", plan, Instant.now().plusSeconds(20));
    }

    private List<TypedHop> hops() {
        return List.of(
            new TypedHop("WORKS_AT", "Company", Direction.OUTGOING),
            new TypedHop("LOCATED_IN", "City", Direction.OUTGOING)
        );
    }

    private void createPath(
        String id,
        String knowledgeBaseId,
        String person,
        long age,
        String company,
        String city,
        String chunkRun,
        String evidenceRun,
        double confidence
    ) {
        neo4jClient.query("""
            CREATE (person:Person {id: $personId, schemaId: 'schema-1', name: $person, age: $age})
            CREATE (company:Company {id: $companyId, schemaId: 'schema-1', name: $company})
            CREATE (city:City {id: $cityId, schemaId: 'schema-1', name: $city})
            CREATE (person)-[works:WORKS_AT {id: $worksId, schemaId: 'schema-1'}]->(company)
            CREATE (company)-[located:LOCATED_IN {id: $locatedId, schemaId: 'schema-1'}]->(city)
            CREATE (chunk:DocumentChunk {
              id: $chunkId,
              kind: 'PARENT',
              knowledgeBaseId: $knowledgeBaseId,
              documentId: $documentId,
              processingRunId: $chunkRun,
              sourceStart: 0,
              sourceEnd: 50,
              pageStart: 1,
              pageEnd: 1,
              effectiveChunkerRevision: 'chunker-v1'
            })
            WITH person, company, city, works, located, chunk,
                 [
                   {factId: $personId, kind: 'NODE', suffix: 'person'},
                   {factId: $companyId, kind: 'NODE', suffix: 'company'},
                   {factId: $cityId, kind: 'NODE', suffix: 'city'},
                   {factId: $worksId, kind: 'RELATIONSHIP', suffix: 'works'},
                   {factId: $locatedId, kind: 'RELATIONSHIP', suffix: 'located'}
                 ] AS facts
            UNWIND facts AS item
            CREATE (e:GraphExtractionEvidence {
              id: $id + '-' + item.suffix,
              canonicalFactId: item.factId,
              factKind: item.kind,
              knowledgeBaseId: $knowledgeBaseId,
              schemaId: 'schema-1',
              sourceChunkId: $chunkId,
              sourceDocumentId: $documentId,
              processingRunId: $evidenceRun,
              confidence: $confidence
            })
            CREATE (chunk)-[:HAS_GRAPH_EVIDENCE]->(e)
            FOREACH (_ IN CASE WHEN item.suffix = 'person' THEN [1] ELSE [] END |
              CREATE (e)-[:ASSERTS_NODE]->(person)
            )
            FOREACH (_ IN CASE WHEN item.suffix = 'company' THEN [1] ELSE [] END |
              CREATE (e)-[:ASSERTS_NODE]->(company)
            )
            FOREACH (_ IN CASE WHEN item.suffix = 'city' THEN [1] ELSE [] END |
              CREATE (e)-[:ASSERTS_NODE]->(city)
            )
            FOREACH (_ IN CASE WHEN item.kind = 'RELATIONSHIP' AND item.suffix = 'works' THEN [1] ELSE [] END |
              CREATE (e)-[:ASSERTS_FROM]->(person)
              CREATE (e)-[:ASSERTS_TO]->(company)
            )
            FOREACH (_ IN CASE WHEN item.kind = 'RELATIONSHIP' AND item.suffix = 'located' THEN [1] ELSE [] END |
              CREATE (e)-[:ASSERTS_FROM]->(company)
              CREATE (e)-[:ASSERTS_TO]->(city)
            )
            """)
            .bind(id).to("id")
            .bind(id + "-person").to("personId")
            .bind(id + "-company").to("companyId")
            .bind(id + "-city").to("cityId")
            .bind(id + "-works").to("worksId")
            .bind(id + "-located").to("locatedId")
            .bind("parent-" + id).to("chunkId")
            .bind("document-" + id).to("documentId")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(person).to("person")
            .bind(age).to("age")
            .bind(company).to("company")
            .bind(city).to("city")
            .bind(chunkRun).to("chunkRun")
            .bind(evidenceRun).to("evidenceRun")
            .bind(confidence).to("confidence")
            .run();
    }

    private void createUnsupportedPathWithoutEvidence() {
        neo4jClient.query("""
            CREATE (person:Person {id: 'missing-person', schemaId: 'schema-1', name: 'Xavier', age: 70})
            CREATE (company:Company {id: 'missing-company', schemaId: 'schema-1', name: 'Missing'})
            CREATE (city:City {id: 'missing-city', schemaId: 'schema-1', name: 'Xanadu'})
            CREATE (person)-[:WORKS_AT {id: 'missing-works', schemaId: 'schema-1'}]->(company)
            CREATE (company)-[:LOCATED_IN {id: 'missing-located', schemaId: 'schema-1'}]->(city)
            """).run();
    }
}
