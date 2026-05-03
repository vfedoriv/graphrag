package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.QueryGenerateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
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

    public QueryController(
        AppProperties appProperties,
        CypherGenerationService cypherGenerationService,
        CypherValidationService cypherValidationService
    ) {
        this.appProperties = appProperties;
        this.cypherGenerationService = cypherGenerationService;
        this.cypherValidationService = cypherValidationService;
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
}
