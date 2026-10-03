package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;

import io.github.vfedoriv.graphrag.search.query.adapters.graph.QueryNeo4jExecutor;
import io.github.vfedoriv.graphrag.search.query.application.CypherExecutionService;
import io.github.vfedoriv.graphrag.search.query.application.CypherValidationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.search.query.api.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.search.query.domain.QueryValidationResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CypherExecutionServiceTest {

    @Test
    void executesValidatedQueryAndMapsRows() {
        CypherValidationService validationService = Mockito.mock(CypherValidationService.class);
        QueryNeo4jExecutor queryNeo4jExecutor = Mockito.mock(QueryNeo4jExecutor.class);

        when(validationService.validate(
            Mockito.eq("kb-1"),
            Mockito.eq("MATCH (c:Contract) RETURN c.contractId AS contractId"),
            Mockito.eq(Map.of()),
            Mockito.any()
        ))
            .thenReturn(new QueryValidationResult(
                true,
                "MATCH (c:Contract) RETURN c.contractId AS contractId LIMIT $__limit",
                Map.of("__limit", 200),
                List.of()
            ));
        when(queryNeo4jExecutor.execute(
            Mockito.eq("MATCH (c:Contract) RETURN c.contractId AS contractId LIMIT $__limit"),
            Mockito.eq(Map.of("__limit", 200)),
            org.mockito.ArgumentMatchers.any()
        ))
            .thenReturn(List.of(Map.of("contractId", "C-1")));

        CypherExecutionService service = new CypherExecutionService(TestRuntimeSettings.from(props()), validationService, queryNeo4jExecutor);
        QueryExecutionResponse response = service.execute("kb-1", "MATCH (c:Contract) RETURN c.contractId AS contractId", Map.of());

        assertThat(response.validation().valid()).isTrue();
        assertThat(response.columns()).containsExactly("contractId");
        assertThat(response.rows()).containsExactly(Map.of("contractId", "C-1"));
        assertThat(response.rowCount()).isEqualTo(1);
    }

    @Test
    void rejectsExecutionWhenValidationFails() {
        CypherValidationService validationService = Mockito.mock(CypherValidationService.class);
        QueryNeo4jExecutor queryNeo4jExecutor = Mockito.mock(QueryNeo4jExecutor.class);
        when(validationService.validate(Mockito.eq("kb-1"), Mockito.eq("MATCH (c:Contract) DELETE c"), Mockito.eq(Map.of()), Mockito.any()))
            .thenReturn(new QueryValidationResult(false, "MATCH (c:Contract) DELETE c", Map.of(), List.of("Blocked keyword")));

        CypherExecutionService service = new CypherExecutionService(TestRuntimeSettings.from(props()), validationService, queryNeo4jExecutor);

        assertThatThrownBy(() -> service.execute("kb-1", "MATCH (c:Contract) DELETE c", Map.of()))
            .isInstanceOf(QueryRejectedException.class)
            .hasMessageContaining("Query validation failed")
            .satisfies(ex -> assertThat(((QueryRejectedException) ex).getErrors())
                .containsExactly("Blocked keyword"));
    }

    @Test
    void propagatesAllValidationErrorsInOriginalOrder() {
        CypherValidationService validationService = Mockito.mock(CypherValidationService.class);
        QueryNeo4jExecutor queryNeo4jExecutor = Mockito.mock(QueryNeo4jExecutor.class);
        when(validationService.validate(Mockito.eq("kb-1"), Mockito.eq("MATCH (x:Unknown) RETURN x.missing"), Mockito.eq(Map.of()), Mockito.any()))
            .thenReturn(new QueryValidationResult(
                false,
                "MATCH (x:Unknown) RETURN x.missing",
                Map.of(),
                List.of("Unknown label: Unknown", "Unknown property: missing")
            ));

        CypherExecutionService service = new CypherExecutionService(TestRuntimeSettings.from(props()), validationService, queryNeo4jExecutor);

        assertThatThrownBy(() -> service.execute("kb-1", "MATCH (x:Unknown) RETURN x.missing", Map.of()))
            .isInstanceOf(QueryRejectedException.class)
            .satisfies(ex -> assertThat(((QueryRejectedException) ex).getErrors())
                .containsExactly("Unknown label: Unknown", "Unknown property: missing"));
    }

    private AppProperties props() {
        return new AppProperties(
            new Neo4jProperties("neo4j"),
            new ModelProperties("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new StorageProperties(Path.of("var/documents")),
            new ChunkingProperties(800, 80, 4000),
            new QueryProperties(200, 15, true, List.of("CREATE", "MERGE", "DELETE")),
            new ExtractionProperties(40, 80, 2)
        );
    }
}
