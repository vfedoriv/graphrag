package io.github.vfedoriv.graphrag.schemas.drafts.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AddDocumentSourceRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AddTextSourceRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisRunResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.ConflictResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.ConflictListScope;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.CandidatePageResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.CreateDraftRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.DecisionRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.DecisionResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.DiffResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.DraftResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.ProjectionResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.ResolveConflictRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.RevisionRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.SourceResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.StartAnalysisResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.UpdateDraftRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.UpdateGuidanceRequest;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftAnalysisService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftReviewService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftSourceService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts")
public class SchemaDraftController {
    private final SchemaDraftLifecycleService lifecycleService;
    private final SchemaDraftSourceService sourceService;
    private final SchemaDraftAnalysisService analysisService;
    private final SchemaDraftReviewService reviewService;

    public SchemaDraftController(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftSourceService sourceService,
        SchemaDraftAnalysisService analysisService,
        SchemaDraftReviewService reviewService
    ) {
        this.lifecycleService = lifecycleService;
        this.sourceService = sourceService;
        this.analysisService = analysisService;
        this.reviewService = reviewService;
    }

    @PostMapping
    public DraftResponse create(
        @PathVariable String knowledgeBaseId, @Valid @RequestBody CreateDraftRequest request
    ) {
        return lifecycleService.create(knowledgeBaseId, request);
    }

    @GetMapping
    public List<DraftResponse> list(@PathVariable String knowledgeBaseId) {
        return lifecycleService.list(knowledgeBaseId);
    }

    @GetMapping("/{draftId}")
    public DraftResponse get(@PathVariable String knowledgeBaseId, @PathVariable String draftId) {
        return lifecycleService.get(knowledgeBaseId, draftId);
    }

    @PutMapping("/{draftId}")
    public DraftResponse update(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody UpdateDraftRequest request
    ) {
        return lifecycleService.update(knowledgeBaseId, draftId, request);
    }

    @PutMapping("/{draftId}/guidance")
    public DraftResponse updateGuidance(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody UpdateGuidanceRequest request
    ) {
        return lifecycleService.updateGuidance(knowledgeBaseId, draftId, request);
    }

    @DeleteMapping("/{draftId}")
    public ResponseEntity<Void> delete(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @RequestParam long revision
    ) {
        lifecycleService.delete(knowledgeBaseId, draftId, revision);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{draftId}/sources/documents")
    public SourceResponse addDocument(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody AddDocumentSourceRequest request
    ) {
        return sourceService.addDocument(knowledgeBaseId, draftId, request.revision(), request.documentId());
    }

    @PostMapping("/{draftId}/sources/text")
    public SourceResponse addText(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody AddTextSourceRequest request
    ) {
        return sourceService.addText(knowledgeBaseId, draftId, request.revision(), request.name(), request.text());
    }

    @PostMapping(path = "/{draftId}/sources/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourceResponse addFile(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @RequestParam long revision, @RequestPart("file") MultipartFile file
    ) {
        return sourceService.addFile(knowledgeBaseId, draftId, revision, file);
    }

    @GetMapping("/{draftId}/sources")
    public List<SourceResponse> sources(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId
    ) {
        return sourceService.list(knowledgeBaseId, draftId);
    }

    @PostMapping("/{draftId}/sources/{sourceId}/refresh")
    public SourceResponse refreshSource(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String sourceId,
        @Valid @RequestBody RevisionRequest request
    ) {
        return sourceService.refreshDocument(knowledgeBaseId, draftId, sourceId, request.revision());
    }

    @DeleteMapping("/{draftId}/sources/{sourceId}")
    public ResponseEntity<Void> removeSource(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String sourceId,
        @RequestParam long revision
    ) {
        sourceService.remove(knowledgeBaseId, draftId, sourceId, revision);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{draftId}/sources/{sourceId}/restore")
    public SourceResponse restoreSource(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String sourceId,
        @Valid @RequestBody RevisionRequest request
    ) {
        return sourceService.restore(knowledgeBaseId, draftId, sourceId, request.revision());
    }

    @PostMapping("/{draftId}/analysis-runs")
    public ResponseEntity<StartAnalysisResponse> startAnalysis(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody RevisionRequest request
    ) {
        StartAnalysisResponse response = analysisService.start(knowledgeBaseId, draftId, request.revision());
        return ResponseEntity.accepted().location(URI.create(response.statusLocation())).body(response);
    }

    @GetMapping("/{draftId}/analysis-runs/{runId}")
    public AnalysisRunResponse analysisStatus(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String runId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return analysisService.get(knowledgeBaseId, draftId, runId, page, size);
    }

    @GetMapping("/{draftId}/analysis-runs")
    public AnalysisRunPageResponse analysisRuns(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return analysisService.list(knowledgeBaseId, draftId, page, size);
    }

    @PostMapping("/{draftId}/analysis-runs/{runId}/retry")
    public ResponseEntity<StartAnalysisResponse> retryAnalysis(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String runId,
        @Valid @RequestBody RevisionRequest request
    ) {
        StartAnalysisResponse response = analysisService.retry(knowledgeBaseId, draftId, runId, request.revision());
        return ResponseEntity.accepted().location(URI.create(response.statusLocation())).body(response);
    }

    @GetMapping("/{draftId}/candidates")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CandidatePageResponse.class)))
    public CandidatePageResponse candidates(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size
    ) {
        return reviewService.candidates(knowledgeBaseId, draftId, page, size);
    }

    @PostMapping("/{draftId}/decisions")
    public DecisionResponse decide(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody DecisionRequest request
    ) {
        return reviewService.decide(knowledgeBaseId, draftId, request);
    }

    @GetMapping("/{draftId}/decisions")
    public List<DecisionResponse> decisions(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId
    ) {
        return reviewService.decisions(knowledgeBaseId, draftId);
    }

    @GetMapping("/{draftId}/conflicts")
    @Operation(
        summary = "List schema draft conflicts",
        description = "Returns only conflicts from the currently promoted aggregate by default. "
            + "Use scope=ALL for deterministic draft-wide history, including non-promoted and superseded aggregates. "
            + "Every item includes aggregateRevisionId and a derived current flag."
    )
    public List<ConflictResponse> conflicts(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Parameter(
            description = "CURRENT (default) returns active review work; ALL returns complete conflict history.",
            example = "CURRENT"
        )
        @RequestParam(defaultValue = "CURRENT") ConflictListScope scope
    ) {
        return reviewService.conflicts(knowledgeBaseId, draftId, scope);
    }

    @PostMapping("/{draftId}/conflicts/{conflictId}/resolution")
    public ConflictResponse resolveConflict(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String conflictId,
        @Valid @RequestBody ResolveConflictRequest request
    ) {
        return reviewService.resolve(knowledgeBaseId, draftId, conflictId, request);
    }

    @GetMapping("/{draftId}/projection")
    public ProjectionResponse projection(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId
    ) {
        return reviewService.projection(knowledgeBaseId, draftId);
    }

    @GetMapping("/{draftId}/diff")
    @Operation(
        summary = "Get the schema draft compatibility diff",
        description = "Returns a diff bound to the current aggregate and draft revision. The baseline descriptor "
            + "identifies an immutable BASE_SCHEMA, PREVIOUS_AGGREGATE, or EMPTY snapshot; baseline.id is null only "
            + "for EMPTY and baseline.contentHash is the SHA-256 of the exact canonical comparison JSON. This response "
            + "expands the prior contract, so strict clients must accept draftRevision and baseline before deployment."
    )
    public DiffResponse diff(@PathVariable String knowledgeBaseId, @PathVariable String draftId) {
        return reviewService.diff(knowledgeBaseId, draftId);
    }

}
