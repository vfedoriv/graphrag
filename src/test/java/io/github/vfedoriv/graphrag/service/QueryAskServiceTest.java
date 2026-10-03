package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;

import io.github.vfedoriv.graphrag.search.query.application.CypherExecutionService;
import io.github.vfedoriv.graphrag.search.query.application.CypherGenerationService;
import io.github.vfedoriv.graphrag.search.query.application.QueryAskService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.search.query.api.model.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryAskResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryValidationResponse;
import io.github.vfedoriv.graphrag.search.query.api.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class QueryAskServiceTest {

    @Test
    void askGeneratesExecutesAndRecordsSuccessAttributes() {
        CypherGenerationService generationService = Mockito.mock(CypherGenerationService.class);
        CypherExecutionService executionService = Mockito.mock(CypherExecutionService.class);
        AiObservationService observationService = Mockito.mock(AiObservationService.class);
        AiObservationScope workflow = Mockito.mock(AiObservationScope.class);
        when(observationService.startWorkflow(Mockito.any())).thenReturn(workflow);
        QueryValidationResponse validation = validValidation();
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
            12
        );
        when(generationService.generate(Mockito.eq("kb-1"), Mockito.eq("list contracts"), Mockito.any())).thenReturn(generated);
        when(executionService.execute(Mockito.eq("kb-1"), Mockito.eq(validation.cypher()), Mockito.eq(validation.parameters()), Mockito.any())).thenReturn(execution);
        QueryAskService service = new QueryAskService(generationService, executionService, observationService, runtimeSettingsService());

        QueryAskResponse response = service.ask("kb-1", "list contracts");

        assertThat(response.generatedQuery()).isSameAs(generated);
        assertThat(response.execution()).isSameAs(execution);
        ArgumentCaptor<AiWorkflowContext> contextCaptor = ArgumentCaptor.forClass(AiWorkflowContext.class);
        verify(observationService).startWorkflow(contextCaptor.capture());
        assertThat(contextCaptor.getValue().workflow()).isEqualTo(AiObservationService.WORKFLOW_QUERY);
        assertThat(contextCaptor.getValue().highCardinalityAttributes())
            .containsEntry("knowledge_base.id", "kb-1")
            .containsEntry("query.prompt.length", "14");
        verify(workflow).highCardinalityAttribute("query.validation.valid", "true");
        verify(workflow).highCardinalityAttribute("query.validation.error_count", "0");
        verify(workflow).highCardinalityAttribute("query.cypher.length", "38");
        verify(workflow).highCardinalityAttribute("query.execution.row_count", "1");
        verify(workflow).highCardinalityAttribute("query.execution.time_ms", "12");
        verify(workflow).success();
        verify(workflow).close();
    }

    @Test
    void askRejectsInvalidGeneratedQueryBeforeExecution() {
        CypherGenerationService generationService = Mockito.mock(CypherGenerationService.class);
        CypherExecutionService executionService = Mockito.mock(CypherExecutionService.class);
        AiObservationService observationService = Mockito.mock(AiObservationService.class);
        AiObservationScope workflow = Mockito.mock(AiObservationScope.class);
        when(observationService.startWorkflow(Mockito.any())).thenReturn(workflow);
        QueryValidationResponse invalid = new QueryValidationResponse(
            false,
            "MATCH (c:Contract) DELETE c",
            Map.of(),
            List.of("Blocked keyword"),
            200,
            15
        );
        when(generationService.generate(Mockito.eq("kb-1"), Mockito.eq("unsafe query"), Mockito.any())).thenReturn(
            new GeneratedQueryResponse("MATCH (c:Contract) DELETE c", "Unsafe", Map.of(), invalid)
        );
        QueryAskService service = new QueryAskService(generationService, executionService, observationService, runtimeSettingsService());

        assertThatThrownBy(() -> service.ask("kb-1", "unsafe query"))
            .isInstanceOf(QueryRejectedException.class)
            .satisfies(ex -> assertThat(((QueryRejectedException) ex).getErrors())
                .containsExactly("Blocked keyword"));

        verify(executionService, never()).execute(Mockito.any(), Mockito.any(), Mockito.any());
        verify(workflow).highCardinalityAttribute("query.validation.valid", "false");
        verify(workflow).highCardinalityAttribute("query.validation.error_count", "1");
        verify(workflow).highCardinalityAttribute("query.cypher.length", "27");
        verify(workflow).error(Mockito.isA(QueryRejectedException.class));
        verify(workflow).close();
    }

    @Test
    void askRecordsRuntimeFailureOnWorkflow() {
        CypherGenerationService generationService = Mockito.mock(CypherGenerationService.class);
        CypherExecutionService executionService = Mockito.mock(CypherExecutionService.class);
        AiObservationService observationService = Mockito.mock(AiObservationService.class);
        AiObservationScope workflow = Mockito.mock(AiObservationScope.class);
        when(observationService.startWorkflow(Mockito.any())).thenReturn(workflow);
        QueryValidationResponse validation = validValidation();
        when(generationService.generate(Mockito.eq("kb-1"), Mockito.eq("list contracts"), Mockito.any())).thenReturn(
            new GeneratedQueryResponse(
                "MATCH (c:Contract) RETURN c.contractId",
                "List contract IDs",
                Map.of(),
                validation
            )
        );
        IllegalStateException failure = new IllegalStateException("execution failed");
        when(executionService.execute(Mockito.eq("kb-1"), Mockito.eq(validation.cypher()), Mockito.eq(validation.parameters()), Mockito.any())).thenThrow(failure);
        QueryAskService service = new QueryAskService(generationService, executionService, observationService, runtimeSettingsService());

        assertThatThrownBy(() -> service.ask("kb-1", "list contracts")).isSameAs(failure);

        verify(workflow).error(failure);
        verify(workflow, never()).success();
        verify(workflow).close();
    }

    private QueryValidationResponse validValidation() {
        return new QueryValidationResponse(
            true,
            "MATCH (c:Contract) RETURN c.contractId LIMIT $__limit",
            Map.of("__limit", 200),
            List.of(),
            200,
            15
        );
    }

    private RuntimeSettingsService runtimeSettingsService() {
        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        when(runtimeSettingsService.query()).thenReturn(new io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess.QuerySettings(
            200, 15, true, List.of("CREATE"), true, 4096, 8, 20, 2, 1));
        return runtimeSettingsService;
    }
}
