package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.CreateRequest;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.ResultResponse;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.RunResponse;
import io.github.vfedoriv.graphrag.dto.PageResponse;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs")
@Tag(name = "Advanced search", description = "Durable, cited, cancellation-aware advanced-search runs.")
public class AdvancedSearchRunController {
    private final AdvancedSearchRunService service;
    public AdvancedSearchRunController(AdvancedSearchRunService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit advanced search", description = "Queues a durable advanced-search run and returns polling, result, and cancellation links.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Run accepted", content = @Content(
            schema = @Schema(implementation = RunResponse.class),
            examples = @ExampleObject(value = "{\"id\":\"run-123\",\"knowledgeBaseId\":\"kb-demo\",\"status\":\"QUEUED\",\"stage\":\"QUEUED\",\"completedBranches\":0,\"totalBranches\":3,\"evidenceCount\":0,\"cancellationRequested\":false,\"links\":{\"self\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123\",\"result\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/result\",\"cancel\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/cancel\"}}")
        )),
        @ApiResponse(responseCode = "429", description = "Advanced-search queue is full")
    })
    public RunResponse create(
        @PathVariable String knowledgeBaseId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Natural-language query and optional evidence controls.",
            required = true,
            content = @Content(
                schema = @Schema(implementation = CreateRequest.class),
                examples = @ExampleObject(value = "{\"query\":\"When does the Acme agreement renew?\",\"maxEvidence\":10,\"includeEvidenceText\":true}")
            )
        )
        @Valid @RequestBody CreateRequest request
    ) {
        return service.create(knowledgeBaseId, request);
    }

    @GetMapping
    @Operation(summary = "List advanced-search runs", description = "Lists owned runs with optional status filtering and paging.")
    public PageResponse<RunResponse> list(
        @PathVariable String knowledgeBaseId,
        @RequestParam(required = false) AdvancedSearchRunStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { return service.list(knowledgeBaseId, status, page, size); }

    @GetMapping("/{runId}")
    @Operation(summary = "Poll advanced-search status", description = "Returns current progress or a terminal COMPLETED, PARTIAL, FAILED, CANCELLED, or INTERRUPTED status.")
    @ApiResponse(responseCode = "200", description = "Current run status", content = @Content(
        schema = @Schema(implementation = RunResponse.class),
        examples = @ExampleObject(name = "partial", value = "{\"id\":\"run-123\",\"knowledgeBaseId\":\"kb-demo\",\"status\":\"PARTIAL\",\"stage\":\"TERMINAL\",\"completedBranches\":2,\"totalBranches\":3,\"evidenceCount\":2,\"cancellationRequested\":false,\"failureCategory\":\"BRANCH_FAILURE\",\"links\":{\"self\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123\",\"result\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/result\"}}")
    ))
    public RunResponse status(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.get(knowledgeBaseId, runId);
    }

    @GetMapping("/{runId}/result")
    @Operation(summary = "Get advanced-search result", description = "Returns completed or partial evidence, answer claims, citations, graph facts, and branch diagnostics.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Completed or partial result", content = @Content(
            schema = @Schema(implementation = ResultResponse.class),
            examples = @ExampleObject(name = "cited-partial-result", value = "{\"runId\":\"run-123\",\"payloadVersion\":1,\"result\":{\"payloadVersion\":1,\"answer\":{\"version\":1,\"status\":\"ANSWERED\",\"text\":\"The renewal date is 2027-01-31.\",\"confidence\":{\"level\":\"HIGH\",\"score\":0.91},\"limitations\":[{\"code\":\"BRANCH_FAILURE\",\"description\":\"Graph retrieval was unavailable.\"}],\"claims\":[{\"id\":\"C1\",\"kind\":\"TEXT\",\"text\":\"The renewal date is 2027-01-31.\",\"citationIds\":[\"E1\"],\"graphFactIds\":[],\"graphEvidenceIds\":[]}]},\"evidence\":[{\"citationId\":\"E1\",\"type\":\"TEXT_CHILD\",\"chunkId\":\"chunk-7\",\"documentId\":\"doc-4\",\"range\":{\"sourceStart\":120,\"sourceEnd\":185,\"pageStart\":2,\"pageEnd\":2},\"text\":\"The agreement renews on 31 January 2027.\",\"rank\":1,\"score\":0.94}],\"contexts\":[],\"graphFacts\":[],\"answerDiagnostics\":{},\"diagnostics\":{\"attempts\":[{\"retriever\":\"GRAPH\",\"status\":\"FAILED\"}]}},\"createdAt\":\"2026-07-31T12:00:00Z\"}")
        )),
        @ApiResponse(responseCode = "409", description = "Run has not produced a result yet")
    })
    public ResultResponse result(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.result(knowledgeBaseId, runId);
    }

    @PostMapping("/{runId}/cancel")
    @Operation(summary = "Cancel advanced search", description = "Requests cancellation idempotently; terminal runs remain unchanged.")
    @ApiResponse(responseCode = "200", description = "Cancellation state", content = @Content(
        schema = @Schema(implementation = RunResponse.class),
        examples = @ExampleObject(value = "{\"id\":\"run-123\",\"knowledgeBaseId\":\"kb-demo\",\"status\":\"RUNNING\",\"stage\":\"RETRIEVING\",\"cancellationRequested\":true,\"links\":{\"self\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123\"}}")
    ))
    public RunResponse cancel(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.cancel(knowledgeBaseId, runId);
    }
}
