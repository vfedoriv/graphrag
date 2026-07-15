package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.CreatePlanRequest;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.RetryPlanRequest;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.StartPlanResponse;
import io.github.vfedoriv.graphrag.service.SchemaReprocessingPlanService;
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
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(knowledgeBaseId, draftId, page, size);
    }

    @PostMapping("/{planId}/retry")
    public ResponseEntity<StartPlanResponse> retry(
        @PathVariable String knowledgeBaseId, @PathVariable String planId,
        @Valid @RequestBody RetryPlanRequest request
    ) {
        StartPlanResponse response = service.retry(knowledgeBaseId, planId, request.resnapshotUnresolvedDocuments());
        return ResponseEntity.accepted().location(URI.create(response.statusLocation())).body(response);
    }
}
