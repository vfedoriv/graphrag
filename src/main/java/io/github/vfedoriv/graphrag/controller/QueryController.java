package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.QueryAskResponse;
import io.github.vfedoriv.graphrag.dto.QueryExecuteRequest;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.QueryGenerateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.service.CypherExecutionService;
import io.github.vfedoriv.graphrag.service.CypherGenerationService;
import io.github.vfedoriv.graphrag.service.CypherValidationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class QueryController {

    private final AppProperties appProperties;
    private final CypherGenerationService cypherGenerationService;
    private final CypherValidationService cypherValidationService;
    private final CypherExecutionService cypherExecutionService;

    public QueryController(
        AppProperties appProperties,
        CypherGenerationService cypherGenerationService,
        CypherValidationService cypherValidationService,
        CypherExecutionService cypherExecutionService
    ) {
        this.appProperties = appProperties;
        this.cypherGenerationService = cypherGenerationService;
        this.cypherValidationService = cypherValidationService;
        this.cypherExecutionService = cypherExecutionService;
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/generate")
    public GeneratedQueryResponse generate(
        @PathVariable String knowledgeBaseId,
        @Valid @RequestBody QueryGenerateRequest request
    ) {
        return cypherGenerationService.generate(knowledgeBaseId, request.prompt());
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/validate")
    public QueryValidationResponse validate(
        @PathVariable String knowledgeBaseId,
        @Valid @RequestBody QueryValidateRequest request
    ) {
        var result = cypherValidationService.validate(knowledgeBaseId, request.cypher(), request.parameters());
        return new QueryValidationResponse(
            result.valid(),
            result.cypher(),
            result.parameters(),
            result.errors(),
            appProperties.query().maxRows(),
            appProperties.query().timeoutSeconds()
        );
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/execute")
    public QueryExecutionResponse execute(
        @PathVariable String knowledgeBaseId,
        @Valid @RequestBody QueryExecuteRequest request
    ) {
        return cypherExecutionService.execute(knowledgeBaseId, request.cypher(), request.parameters());
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/ask")
    public QueryAskResponse ask(
        @PathVariable String knowledgeBaseId,
        @Valid @RequestBody QueryGenerateRequest request
    ) {
        GeneratedQueryResponse generated = cypherGenerationService.generate(knowledgeBaseId, request.prompt());
        if (!generated.validation().valid()) {
            throw new QueryRejectedException(generated.validation().errors());
        }
        QueryExecutionResponse execution = cypherExecutionService.execute(
            knowledgeBaseId,
            generated.validation().cypher(),
            generated.validation().parameters()
        );
        return new QueryAskResponse(generated, execution);
    }
}
