package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.NodeProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.error.QueryDeadlineExceededException;
import io.github.vfedoriv.graphrag.repository.GraphRetrievalRepository;
import io.github.vfedoriv.graphrag.repository.GraphRetrievalRepository.Query;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidatedGraphPlan;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdvancedSearchGraphRetrieverTest {

    private GraphPlanValidationService validationService;
    private GraphPlanCypherRenderer renderer;
    private GraphRetrievalRepository repository;
    private AdvancedSearchGraphRetriever retriever;
    private GraphPlan plan;

    @BeforeEach
    void setUp() {
        validationService = mock(GraphPlanValidationService.class);
        renderer = mock(GraphPlanCypherRenderer.class);
        repository = mock(GraphRetrievalRepository.class);
        retriever = new AdvancedSearchGraphRetriever(validationService, renderer, repository);
        plan = new GraphPlan("Person", List.of(), List.of(), List.of(new NodeProjection(0, "person")), null,
            List.of(), 3);
        ValidatedGraphPlan validated = new ValidatedGraphPlan(
            plan,
            new ActiveSchemaContext("kb-1", "schema-1", null, null),
            10,
            Duration.ofSeconds(5)
        );
        when(validationService.validate("kb-1", plan)).thenReturn(new ValidationResult(true, List.of(), validated));
        when(renderer.render(validated)).thenReturn(new Query("RETURN 1", Map.of()));
    }

    @Test
    void mapsOnlyPersistedParentCitationsAndDoesNotExposeExecutableQuery() {
        when(repository.execute(any(), any())).thenReturn(List.of(Map.of(
            "values", Map.of("name", "Alice"),
            "facts", List.of(Map.of(
                "canonicalFactId", "person-1",
                "factKind", "NODE",
                "schemaType", "Person",
                "properties", Map.of("name", "Alice"),
                "supports", List.of(Map.of(
                    "evidenceId", "evidence-1",
                    "chunkId", "parent-1",
                    "documentId", "doc-1",
                    "chunkKind", "PARENT",
                    "sourceStart", 10,
                    "sourceEnd", 20,
                    "processingRunId", "run-1"
                ))
            ))
        )));

        io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Result result =
            retriever.retrieve(request());

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().facts().getFirst().evidenceIds()).containsExactly("evidence-1");
        assertThat(result.rows().getFirst().facts().getFirst().citations().getFirst().chunkId()).isEqualTo("parent-1");
        assertThat(result.toString()).doesNotContain("RETURN 1");
    }

    @Test
    void omitsFactsBackedByChildOrMalformedSourceRanges() {
        when(repository.execute(any(), any())).thenReturn(List.of(Map.of(
            "values", Map.of("name", "Alice"),
            "facts", List.of(Map.of(
                "canonicalFactId", "person-1",
                "factKind", "NODE",
                "schemaType", "Person",
                "properties", Map.of(),
                "supports", List.of(Map.of(
                    "evidenceId", "evidence-1",
                    "chunkId", "child-1",
                    "documentId", "doc-1",
                    "chunkKind", "CHILD",
                    "sourceStart", 20,
                    "sourceEnd", 10,
                    "processingRunId", "run-1"
                ))
            ))
        )));

        io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Result result =
            retriever.retrieve(request());

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.rows()).isEmpty();
    }

    @Test
    void reportsTimeoutWithoutQueryOrContent() {
        when(repository.execute(any(), any())).thenThrow(new QueryDeadlineExceededException(null));

        io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Result result =
            retriever.retrieve(request());

        assertThat(result.diagnostics().status()).isEqualTo(Status.DEADLINE_EXCEEDED);
        assertThat(result.rows()).isEmpty();
        assertThat(result.diagnostics().failureCategory()).isEqualTo("QueryDeadlineExceededException");
    }

    private Request request() {
        return new Request("kb-1", plan, Instant.now().plusSeconds(10));
    }
}
