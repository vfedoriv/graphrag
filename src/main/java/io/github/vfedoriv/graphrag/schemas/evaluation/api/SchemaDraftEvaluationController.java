package io.github.vfedoriv.graphrag.schemas.evaluation.api;

import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.RevisionRequest;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationRunResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.StartEvaluationRequest;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.StartEvaluationResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.application.SchemaDraftEvaluationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts")
public class SchemaDraftEvaluationController {
    private final SchemaDraftEvaluationService evaluationService;
    public SchemaDraftEvaluationController(SchemaDraftEvaluationService evaluationService) { this.evaluationService = evaluationService; }
    @PostMapping("/{draftId}/evaluation-runs")
    @Operation(
        summary = "Start held-out schema draft evaluation",
        description = "Starts evaluation only when discovery analysis is current and every selected document has "
            + "a SHA-256 that did not successfully contribute to the current draft aggregate. Exact binary hashes "
            + "are compared across DOCUMENT, FILE, and TEXT discovery sources."
    )
    public ResponseEntity<StartEvaluationResponse> startEvaluation(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody StartEvaluationRequest request
    ) {
        StartEvaluationResponse response = evaluationService.start(knowledgeBaseId, draftId, request);
        return ResponseEntity.accepted().location(URI.create(response.statusLocation())).body(response);
    }

    @GetMapping("/{draftId}/evaluation-runs/{runId}")
    public EvaluationRunResponse evaluationStatus(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String runId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return evaluationService.get(knowledgeBaseId, draftId, runId, page, size);
    }

    @GetMapping("/{draftId}/evaluation-runs")
    public EvaluationRunPageResponse evaluationRuns(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return evaluationService.list(knowledgeBaseId, draftId, page, size);
    }

    @GetMapping("/{draftId}/evaluation-eligible-documents")
    @Operation(
        summary = "List held-out evaluation candidates",
        description = "Returns knowledge-base documents with draft-wide evaluation readiness and per-document "
            + "eligibility. ACTIVE_DISCOVERY_EVIDENCE means the exact SHA-256 contributed to the current aggregate. "
            + "DRAFT_ANALYSIS_REQUIRED means discovery must be analyzed again before any document can be selected."
    )
    public EvaluationEligibleDocumentPageResponse evaluationEligibleDocuments(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return evaluationService.eligibleDocuments(knowledgeBaseId, draftId, page, size);
    }

    @PostMapping("/{draftId}/evaluation-runs/{runId}/retry")
    public ResponseEntity<StartEvaluationResponse> retryEvaluation(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId, @PathVariable String runId,
        @Valid @RequestBody RevisionRequest request
    ) {
        StartEvaluationResponse response = evaluationService.retry(
            knowledgeBaseId, draftId, runId, request.revision());
        return ResponseEntity.accepted().location(URI.create(response.statusLocation())).body(response);
    }

}
