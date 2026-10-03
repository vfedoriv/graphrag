package io.github.vfedoriv.graphrag.search.runs.api;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchRunDtos.CreateRequest;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchRunDtos.ResultResponse;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchRunDtos.RunDetailResponse;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchRunDtos.RunSummaryResponse;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchReadinessDtos.ReadinessResponse;
import io.github.vfedoriv.graphrag.http.contracts.PageResponse;
import io.github.vfedoriv.graphrag.search.runs.application.AdvancedSearchReadinessService;
import io.github.vfedoriv.graphrag.search.runs.application.AdvancedSearchRunService;
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
    private final AdvancedSearchReadinessService readinessService;
    public AdvancedSearchRunController(
        AdvancedSearchRunService service,
        AdvancedSearchReadinessService readinessService
    ) {
        this.service = service;
        this.readinessService = readinessService;
    }

    @GetMapping("/readiness")
    @Operation(
        summary = "Inspect advanced-search readiness",
        description = "Returns deterministic provider, embedding, corpus, and graph-branch readiness without contacting a provider."
    )
    @ApiResponse(responseCode = "200", description = "Readiness evaluated", content = @Content(
        schema = @Schema(implementation = ReadinessResponse.class),
        examples = @ExampleObject(value = "{\"knowledgeBaseId\":\"kb-demo\",\"ready\":true,\"profileId\":\"default\",\"profileRevision\":3,\"graphBranchAvailable\":false,\"embeddedCorpusPresent\":false,\"blockers\":[],\"informational\":[{\"code\":\"SCHEMA_UNAVAILABLE\",\"description\":\"No active schema is available; text retrieval remains available.\"},{\"code\":\"EMPTY_CORPUS\",\"description\":\"No embedded chunks are available; the run may complete with insufficient evidence.\"}]}" )
    ))
    public ReadinessResponse readiness(@PathVariable String knowledgeBaseId) {
        return readinessService.evaluate(knowledgeBaseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit advanced search", description = "Queues a durable advanced-search run and returns polling, result, and cancellation links.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Run accepted", content = @Content(
            schema = @Schema(implementation = RunDetailResponse.class),
            examples = @ExampleObject(value = "{\"id\":\"run-123\",\"knowledgeBaseId\":\"kb-demo\",\"query\":\"When does the Acme agreement renew?\",\"maximumEvidence\":10,\"includeEvidenceText\":true,\"status\":\"QUEUED\",\"stage\":\"QUEUED\",\"completedBranches\":0,\"totalBranches\":3,\"evidenceCount\":0,\"cancellationRequested\":false,\"links\":{\"self\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123\",\"result\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/result\",\"cancel\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/cancel\"}}")
        )),
        @ApiResponse(responseCode = "429", description = "Advanced-search queue is full")
    })
    public RunDetailResponse create(
        @PathVariable String knowledgeBaseId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Natural-language query and optional evidence controls.",
            required = true,
            content = @Content(
                schema = @Schema(implementation = CreateRequest.class),
                examples = @ExampleObject(value = "{\"query\":\"When does the Acme agreement renew?\",\"maximumEvidence\":10,\"includeEvidenceText\":true}")
            )
        )
        @Valid @RequestBody CreateRequest request
    ) {
        return service.create(knowledgeBaseId, request);
    }

    @GetMapping
    @Operation(summary = "List advanced-search runs", description = "Lists owned runs with optional status filtering and paging.")
    public PageResponse<RunSummaryResponse> list(
        @PathVariable String knowledgeBaseId,
        @RequestParam(required = false) AdvancedSearchRunStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { return service.list(knowledgeBaseId, status, page, size); }

    @GetMapping("/{runId}")
    @Operation(summary = "Poll advanced-search status", description = "Returns current progress or a terminal COMPLETED, PARTIAL, FAILED, CANCELLED, or INTERRUPTED status.")
    @ApiResponse(responseCode = "200", description = "Current run status", content = @Content(
        schema = @Schema(implementation = RunDetailResponse.class),
        examples = @ExampleObject(name = "partial", value = "{\"id\":\"run-123\",\"knowledgeBaseId\":\"kb-demo\",\"query\":\"When does the Acme agreement renew?\",\"maximumEvidence\":10,\"includeEvidenceText\":true,\"status\":\"PARTIAL\",\"stage\":\"TERMINAL\",\"completedBranches\":2,\"totalBranches\":3,\"evidenceCount\":2,\"cancellationRequested\":false,\"failureCategory\":\"BRANCH_FAILURE\",\"links\":{\"self\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123\",\"result\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/result\"}}")
    ))
    public RunDetailResponse status(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.get(knowledgeBaseId, runId);
    }

    @GetMapping("/{runId}/result")
    @Operation(summary = "Get advanced-search result", description = "Returns completed or partial evidence, answer claims, citations, graph facts, and branch diagnostics.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Completed or partial result", content = @Content(
            schema = @Schema(implementation = ResultResponse.class),
        examples = @ExampleObject(name = "cited-partial-result", value = "{\"runId\":\"run-123\",\"payloadVersion\":1,\"result\":{\"payloadVersion\":1,\"answer\":{\"version\":1,\"status\":\"ANSWERED\",\"text\":\"The renewal date is 2027-01-31.\",\"confidence\":{\"level\":\"HIGH\",\"score\":0.91},\"limitations\":[{\"code\":\"BRANCH_FAILURE\",\"description\":\"Graph retrieval was unavailable.\"}],\"claims\":[{\"id\":\"C1\",\"kind\":\"TEXT\",\"text\":\"The renewal date is 2027-01-31.\",\"citationIds\":[\"E1\"],\"graphFactIds\":[],\"graphEvidenceIds\":[]}]},\"evidence\":[{\"citationId\":\"E1\",\"type\":\"TEXT_CHILD\",\"chunkId\":\"chunk-7\",\"documentId\":\"doc-4\",\"range\":{\"sourceStart\":120,\"sourceEnd\":185,\"pageStart\":2,\"pageEnd\":2},\"processingRunId\":\"process-2\",\"effectiveChunkerRevision\":\"chunker-v3\",\"structuralPath\":\"section-2\",\"text\":\"The agreement renews on 31 January 2027.\",\"rank\":1,\"score\":0.94,\"sourceFilename\":\"acme-agreement.pdf\",\"sourceContentType\":\"application/pdf\",\"sourceDisplayLabel\":\"acme-agreement.pdf\"}],\"contexts\":[],\"graphFacts\":[],\"answerDiagnostics\":{\"repairAttempted\":false,\"repairSucceeded\":false,\"abstained\":false,\"citationCount\":1,\"claimCount\":1,\"outcomeCategory\":\"ANSWERED\"},\"diagnostics\":{\"attempts\":[{\"roundNumber\":1,\"subqueryId\":\"q1\",\"retriever\":\"GRAPH\",\"status\":\"FAILED\",\"candidateCount\":0,\"latencyMs\":12,\"failureCategory\":\"UNAVAILABLE\"}]}},\"createdAt\":\"2026-07-31T12:00:00Z\"}")
        )),
        @ApiResponse(responseCode = "409", description = "Run has not produced a result yet")
    })
    public ResultResponse result(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.result(knowledgeBaseId, runId);
    }

    @PostMapping("/{runId}/cancel")
    @Operation(summary = "Cancel advanced search", description = "Requests cancellation idempotently; terminal runs remain unchanged.")
    @ApiResponse(responseCode = "200", description = "Cancellation state", content = @Content(
        schema = @Schema(implementation = RunSummaryResponse.class),
        examples = @ExampleObject(value = "{\"id\":\"run-123\",\"knowledgeBaseId\":\"kb-demo\",\"queryPreview\":\"When does the Acme agreement renew?\",\"maximumEvidence\":10,\"includeEvidenceText\":true,\"status\":\"RUNNING\",\"stage\":\"RETRIEVING\",\"completedBranches\":1,\"totalBranches\":3,\"evidenceCount\":2,\"cancellationRequested\":true,\"failureCategory\":null,\"deadlineAt\":\"2026-08-01T12:01:00Z\",\"createdAt\":\"2026-08-01T12:00:00Z\",\"startedAt\":\"2026-08-01T12:00:01Z\",\"completedAt\":null,\"links\":{\"self\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123\",\"result\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/result\",\"cancel\":\"/api/v1/knowledge-bases/kb-demo/queries/advanced-search-runs/run-123/cancel\"}}")
    ))
    public RunSummaryResponse cancel(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.cancel(knowledgeBaseId, runId);
    }
}
