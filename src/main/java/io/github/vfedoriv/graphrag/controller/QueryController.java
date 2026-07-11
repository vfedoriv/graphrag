package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.dto.QueryAskResponse;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.QueryExecuteRequest;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.HybridSearchRequest;
import io.github.vfedoriv.graphrag.dto.HybridSearchResponse;
import io.github.vfedoriv.graphrag.dto.QueryGenerateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidateRequest;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.service.CypherExecutionService;
import io.github.vfedoriv.graphrag.service.CypherGenerationService;
import io.github.vfedoriv.graphrag.service.CypherValidationService;
import io.github.vfedoriv.graphrag.service.HybridSearchService;
import io.github.vfedoriv.graphrag.service.QueryAskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Queries", description = "Natural-language to Cypher workflow: generate, validate, execute, and ask.")
@Slf4j
public class QueryController {

    private final CypherGenerationService cypherGenerationService;
    private final CypherValidationService cypherValidationService;
    private final CypherExecutionService cypherExecutionService;
    private final QueryAskService queryAskService;
    private final HybridSearchService hybridSearchService;

    @Autowired
    public QueryController(
        CypherGenerationService cypherGenerationService,
        CypherValidationService cypherValidationService,
        CypherExecutionService cypherExecutionService,
        QueryAskService queryAskService,
        HybridSearchService hybridSearchService
    ) {
        this.cypherGenerationService = cypherGenerationService;
        this.cypherValidationService = cypherValidationService;
        this.cypherExecutionService = cypherExecutionService;
        this.queryAskService = queryAskService;
        this.hybridSearchService = hybridSearchService;
    }

    public QueryController(
        AppProperties ignored,
        CypherGenerationService cypherGenerationService,
        CypherValidationService cypherValidationService,
        CypherExecutionService cypherExecutionService,
        QueryAskService queryAskService,
        HybridSearchService hybridSearchService
    ) {
        this(cypherGenerationService, cypherValidationService, cypherExecutionService, queryAskService, hybridSearchService);
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/generate")
    @Operation(summary = "Generate Cypher", description = "Generates a Cypher query from a natural language prompt using active schema context.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Query generated",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GeneratedQueryResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"cypher\":\"MATCH (c:Contract) RETURN c.title LIMIT 10\",\"explanation\":\"Returns first 10 contract titles.\",\"parameters\":{},\"validation\":{\"valid\":true,\"cypher\":\"MATCH (c:Contract) RETURN c.title LIMIT 10\",\"parameters\":{},\"errors\":[],\"maxRows\":200,\"timeoutSeconds\":15}}"
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content(schema = @Schema()))
    })
    public GeneratedQueryResponse generate(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Natural language prompt to translate into Cypher.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = QueryGenerateRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"prompt\":\"List active contracts\"}"
                )
            )
        )
        @Valid @RequestBody QueryGenerateRequest request
    ) {
        log.info(
            "Generate query request: knowledgeBaseId={}, promptLength={}, promptFingerprint={}",
            knowledgeBaseId,
            LogMetadata.length(request.prompt()),
            LogMetadata.fingerprint(request.prompt())
        );
        GeneratedQueryResponse response = cypherGenerationService.generate(knowledgeBaseId, request.prompt());
        log.info(
            "Generate query completed: knowledgeBaseId={}, valid={}, errorCount={}, cypherLength={}",
            knowledgeBaseId,
            response.validation().valid(),
            response.validation().errors().size(),
            LogMetadata.length(response.cypher())
        );
        return response;
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/validate")
    @Operation(summary = "Validate Cypher", description = "Validates Cypher against safety rules and schema-awareness constraints.")
    @ApiResponse(
        responseCode = "200",
        description = "Validation result returned",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = QueryValidationResponse.class),
            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                value = "{\"valid\":true,\"cypher\":\"MATCH (c:Contract) RETURN c.title LIMIT 10\",\"parameters\":{},\"errors\":[],\"maxRows\":200,\"timeoutSeconds\":15}"
            )
        )
    )
    public QueryValidationResponse validate(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Cypher query and parameters to validate.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = QueryValidateRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"cypher\":\"MATCH (c:Contract) RETURN c.title LIMIT 10\",\"parameters\":{}}"
                )
            )
        )
        @Valid @RequestBody QueryValidateRequest request
    ) {
        log.info(
            "Validate query request: knowledgeBaseId={}, cypherLength={}, parameterCount={}",
            knowledgeBaseId,
            LogMetadata.length(request.cypher()),
            request.parameters() == null ? 0 : request.parameters().size()
        );
        QueryValidationResponse response = cypherExecutionService.toValidationResponse(
            cypherValidationService.validate(knowledgeBaseId, request.cypher(), request.parameters())
        );
        log.info(
            "Validate query completed: knowledgeBaseId={}, valid={}, errorCount={}",
            knowledgeBaseId,
            response.valid(),
            response.errors().size()
        );
        return response;
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/execute")
    @Operation(summary = "Execute Cypher", description = "Executes validated read-only Cypher query and returns rows with metadata.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Query executed"),
        @ApiResponse(responseCode = "400", description = "Query rejected by validation", content = @Content(schema = @Schema()))
    })
    public QueryExecutionResponse execute(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Cypher query and parameters to execute.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = QueryExecuteRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"cypher\":\"MATCH (c:Contract) RETURN c.title LIMIT 10\",\"parameters\":{}}"
                )
            )
        )
        @Valid @RequestBody QueryExecuteRequest request
    ) {
        log.info(
            "Execute query request: knowledgeBaseId={}, cypherLength={}, parameterCount={}",
            knowledgeBaseId,
            LogMetadata.length(request.cypher()),
            request.parameters() == null ? 0 : request.parameters().size()
        );
        QueryExecutionResponse response = cypherExecutionService.execute(knowledgeBaseId, request.cypher(), request.parameters());
        log.info(
            "Execute query completed: knowledgeBaseId={}, rowCount={}, executionTimeMs={}",
            knowledgeBaseId,
            response.rowCount(),
            response.executionTimeMs()
        );
        return response;
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/ask")
    @Operation(summary = "Ask question", description = "One-shot flow: generate Cypher from prompt, validate it, then execute and return combined result.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Question answered"),
        @ApiResponse(responseCode = "400", description = "Generated query failed validation", content = @Content(schema = @Schema()))
    })
    public QueryAskResponse ask(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Natural language question to run end-to-end (generate + validate + execute).",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = QueryGenerateRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"prompt\":\"Which parties are linked to contract C-1001?\"}"
                )
            )
        )
        @Valid @RequestBody QueryGenerateRequest request
    ) {
        log.info(
            "Ask query request: knowledgeBaseId={}, promptLength={}, promptFingerprint={}",
            knowledgeBaseId,
            LogMetadata.length(request.prompt()),
            LogMetadata.fingerprint(request.prompt())
        );
        return queryAskService.ask(knowledgeBaseId, request.prompt());
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/queries/hybrid-search")
    @Operation(summary = "Hybrid search", description = "Embeds a search query, retrieves ranked document chunks, and expands graph context.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Hybrid search results returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = HybridSearchResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content(schema = @Schema()))
    })
    public HybridSearchResponse hybridSearch(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Natural language search text and retrieval bounds.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = HybridSearchRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"query\":\"Acme renewal terms\",\"topK\":10,\"graphDepth\":1,\"includeChunkText\":true}"
                )
            )
        )
        @Valid @RequestBody HybridSearchRequest request
    ) {
        log.info(
            "Hybrid search request: knowledgeBaseId={}, queryLength={}, queryFingerprint={}, topK={}, graphDepth={}, includeChunkText={}",
            knowledgeBaseId,
            LogMetadata.length(request.query()),
            LogMetadata.fingerprint(request.query()),
            request.topK(),
            request.graphDepth(),
            request.includeChunkText()
        );
        HybridSearchResponse response = hybridSearchService.search(knowledgeBaseId, request);
        log.info(
            "Hybrid search completed: knowledgeBaseId={}, hitCount={}, executionTimeMs={}",
            knowledgeBaseId,
            response.hitCount(),
            response.executionTimeMs()
        );
        return response;
    }
}
