package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.search.query.api.QueryController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.search.query.api.model.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryAskResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryGenerateRequest;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryValidationResponse;
import io.github.vfedoriv.graphrag.search.query.application.CypherExecutionService;
import io.github.vfedoriv.graphrag.search.query.application.CypherGenerationService;
import io.github.vfedoriv.graphrag.search.query.application.CypherValidationService;
import io.github.vfedoriv.graphrag.search.query.application.QueryAskService;
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
            Mockito.mock(CypherGenerationService.class),
            Mockito.mock(CypherValidationService.class),
            Mockito.mock(CypherExecutionService.class),
            queryAskService
        );
        QueryAskResponse response = controller.ask("kb-1", new QueryGenerateRequest("list contracts"));

        assertThat(response).isSameAs(expected);
        Mockito.verify(queryAskService).ask("kb-1", "list contracts");
    }

}
