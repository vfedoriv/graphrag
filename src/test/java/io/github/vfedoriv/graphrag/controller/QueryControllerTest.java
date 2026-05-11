package io.github.vfedoriv.graphrag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.QueryAskResponse;
import io.github.vfedoriv.graphrag.dto.QueryGenerateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.service.CypherExecutionService;
import io.github.vfedoriv.graphrag.service.CypherGenerationService;
import io.github.vfedoriv.graphrag.service.CypherValidationService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QueryControllerTest {

    @Test
    void askOrchestratesGenerateThenExecute() {
        CypherGenerationService generationService = Mockito.mock(CypherGenerationService.class);
        CypherExecutionService executionService = Mockito.mock(CypherExecutionService.class);

        QueryValidationResponse validation = new QueryValidationResponse(
            true,
            "MATCH (c:Contract) RETURN c.contractId LIMIT $__limit",
            Map.of("__limit", 200),
            List.of(),
            200,
            15
        );
        when(generationService.generate("kb-1", "list contracts")).thenReturn(
            new GeneratedQueryResponse(
                "MATCH (c:Contract) RETURN c.contractId",
                "List contract IDs",
                Map.of(),
                validation
            )
        );
        when(executionService.execute("kb-1", validation.cypher(), validation.parameters()))
            .thenReturn(new io.github.vfedoriv.graphrag.dto.QueryExecutionResponse(
                validation.cypher(),
                validation.parameters(),
                validation,
                List.of("contractId"),
                List.of(Map.of("contractId", "C-1")),
                1,
                1
            ));

        QueryController controller = new QueryController(
            props(),
            generationService,
            Mockito.mock(CypherValidationService.class),
            executionService
        );
        QueryAskResponse response = controller.ask("kb-1", new QueryGenerateRequest("list contracts"));

        assertThat(response.generatedQuery().validation().valid()).isTrue();
        assertThat(response.execution().rows()).containsExactly(Map.of("contractId", "C-1"));
    }

    @Test
    void askRejectsWhenGeneratedQueryIsInvalid() {
        CypherGenerationService generationService = Mockito.mock(CypherGenerationService.class);
        QueryValidationResponse invalid = new QueryValidationResponse(
            false,
            "MATCH (c:Contract) DELETE c",
            Map.of(),
            List.of("Blocked keyword"),
            200,
            15
        );
        when(generationService.generate("kb-1", "unsafe query")).thenReturn(
            new GeneratedQueryResponse("MATCH (c:Contract) DELETE c", "Unsafe", Map.of(), invalid)
        );

        QueryController controller = new QueryController(
            props(),
            generationService,
            Mockito.mock(CypherValidationService.class),
            Mockito.mock(CypherExecutionService.class)
        );

        assertThatThrownBy(() -> controller.ask("kb-1", new QueryGenerateRequest("unsafe query")))
            .isInstanceOf(QueryRejectedException.class);
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
