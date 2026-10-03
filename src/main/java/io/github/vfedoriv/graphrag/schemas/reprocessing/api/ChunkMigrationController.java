package io.github.vfedoriv.graphrag.schemas.reprocessing.api;

import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.ChunkMigrationPreviewRequest;
import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.ChunkMigrationPreviewResponse;
import io.github.vfedoriv.graphrag.schemas.reprocessing.application.SchemaReprocessingPlanService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/chunk-migrations")
public class ChunkMigrationController {
    private final SchemaReprocessingPlanService service;

    public ChunkMigrationController(SchemaReprocessingPlanService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    @Operation(
        summary = "Preview a chunk migration",
        description = "Read-only selection and readiness preview; it creates no plan or processing work.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            value = "{\"selection\":\"OUTDATED_STRATEGY\",\"documentIds\":[],\"processingOptions\":{}}"
        )))
    )
    public ChunkMigrationPreviewResponse preview(
        @PathVariable String knowledgeBaseId,
        @Valid @RequestBody ChunkMigrationPreviewRequest request,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.preview(knowledgeBaseId, request, page, size);
    }
}
