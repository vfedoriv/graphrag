package io.github.vfedoriv.graphrag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.QueryAskResponse;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.dto.QueryGenerateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.service.CypherExecutionService;
import io.github.vfedoriv.graphrag.service.CypherGenerationService;
import io.github.vfedoriv.graphrag.service.CypherValidationService;
import io.github.vfedoriv.graphrag.service.QueryAskService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QueryControllerTest {

    @Test
    void askDelegatesToQueryAskService() {
        QueryAskService queryAskService = Mockito.mock(QueryAskService.class);
        QueryValidationResponse validation = new QueryValidationResponse(
            true,
            "MATCH (c:Contract) RETURN c.contractId LIMIT $__limit",
            Map.of("__limit", 200),
            List.of(),
            200,
            15
        );
        GeneratedQueryResponse generated = new GeneratedQueryResponse(
            "MATCH (c:Contract) RETURN c.contractId",
            "List contract IDs",
            Map.of(),
            validation
        );
        QueryExecutionResponse execution = new QueryExecutionResponse(
            validation.cypher(),
            validation.parameters(),
            validation,
            List.of("contractId"),
            List.of(Map.of("contractId", "C-1")),
            1,
            1
        );
        QueryAskResponse expected = new QueryAskResponse(generated, execution);
        when(queryAskService.ask("kb-1", "list contracts")).thenReturn(expected);

        QueryController controller = new QueryController(
            props(),
            Mockito.mock(CypherGenerationService.class),
            Mockito.mock(CypherValidationService.class),
            Mockito.mock(CypherExecutionService.class),
            queryAskService
        );
        QueryAskResponse response = controller.ask("kb-1", new QueryGenerateRequest("list contracts"));

        assertThat(response).isSameAs(expected);
        Mockito.verify(queryAskService).ask("kb-1", "list contracts");
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
