package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.data.neo4j.core.Neo4jClient;

class CypherExecutionServiceTest {

    @Test
    void executesValidatedQueryAndMapsRows() {
        CypherValidationService validationService = Mockito.mock(CypherValidationService.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class, Answers.RETURNS_DEEP_STUBS);

        when(validationService.validate("kb-1", "MATCH (c:Contract) RETURN c.contractId AS contractId", Map.of()))
            .thenReturn(new QueryValidationResult(
                true,
                "MATCH (c:Contract) RETURN c.contractId AS contractId LIMIT $__limit",
                Map.of("__limit", 200),
                List.of()
            ));
        when(neo4jClient.query("MATCH (c:Contract) RETURN c.contractId AS contractId LIMIT $__limit")
            .bindAll(Map.of("__limit", 200))
            .fetch()
            .all())
            .thenReturn(List.of(Map.of("contractId", "C-1")));

        CypherExecutionService service = new CypherExecutionService(props(), validationService, neo4jClient);
        QueryExecutionResponse response = service.execute("kb-1", "MATCH (c:Contract) RETURN c.contractId AS contractId", Map.of());

        assertThat(response.validation().valid()).isTrue();
        assertThat(response.columns()).containsExactly("contractId");
        assertThat(response.rows()).containsExactly(Map.of("contractId", "C-1"));
        assertThat(response.rowCount()).isEqualTo(1);
    }

    @Test
    void rejectsExecutionWhenValidationFails() {
        CypherValidationService validationService = Mockito.mock(CypherValidationService.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class, Answers.RETURNS_DEEP_STUBS);
        when(validationService.validate("kb-1", "MATCH (c:Contract) DELETE c", Map.of()))
            .thenReturn(new QueryValidationResult(false, "MATCH (c:Contract) DELETE c", Map.of(), List.of("Blocked keyword")));

        CypherExecutionService service = new CypherExecutionService(props(), validationService, neo4jClient);

        assertThatThrownBy(() -> service.execute("kb-1", "MATCH (c:Contract) DELETE c", Map.of()))
            .isInstanceOf(QueryRejectedException.class)
            .hasMessageContaining("Query validation failed");
    }

    private AppProperties props() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE", "MERGE", "DELETE")),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
