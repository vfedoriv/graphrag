package io.github.vfedoriv.graphrag.schemas.reprocessing.api;

import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.CreatePlanRequest;
import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.PlanResponse;
import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.PlanPageResponse;
import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.RetryPlanRequest;
import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.StartPlanResponse;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.schemas.reprocessing.application.SchemaReprocessingPlanService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans")
public class SchemaReprocessingPlanController {
    private final SchemaReprocessingPlanService service;

    public SchemaReprocessingPlanController(SchemaReprocessingPlanService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<StartPlanResponse> create(
        @PathVariable String knowledgeBaseId, @Valid @RequestBody CreatePlanRequest request
    ) {
        StartPlanResponse response = service.create(knowledgeBaseId, request);
        return ResponseEntity.accepted().location(URI.create(response.statusLocation())).body(response);
    }

    @GetMapping("/{planId}")
    public PlanResponse get(
        @PathVariable String knowledgeBaseId, @PathVariable String planId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return service.get(knowledgeBaseId, planId, page, size);
    }

    @GetMapping
    public PlanPageResponse list(
        @PathVariable String knowledgeBaseId,
        @RequestParam(required = false) String draftId,
        @RequestParam(required = false) ReprocessingPlanReason reason,
        @RequestParam(required = false) ChunkReprocessingSelection selection,
        @RequestParam(required = false) SchemaReprocessingPlanStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(knowledgeBaseId, draftId, reason, selection, status, page, size);
    }

    @PostMapping("/{planId}/retry")
    @Operation(
        summary = "Retry unresolved reprocessing documents",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
            @ExampleObject(name = "explicit", value = "{\"mode\":\"RESNAPSHOT_UNRESOLVED\"}"),
            @ExampleObject(name = "deprecatedCompatibility",
                value = "{\"resnapshotUnresolvedDocuments\":true}")
        }))
    )
    public ResponseEntity<StartPlanResponse> retry(
        @PathVariable String knowledgeBaseId, @PathVariable String planId,
        @Valid @RequestBody RetryPlanRequest request
    ) {
        StartPlanResponse response = service.retry(knowledgeBaseId, planId, request);
        return ResponseEntity.accepted().location(URI.create(response.statusLocation())).body(response);
    }
}
