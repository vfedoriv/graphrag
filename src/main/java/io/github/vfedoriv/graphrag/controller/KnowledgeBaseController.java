package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.dto.AiProfileResponse;
import io.github.vfedoriv.graphrag.dto.CreateKnowledgeBaseRequest;
import io.github.vfedoriv.graphrag.dto.KnowledgeBaseResponse;
import io.github.vfedoriv.graphrag.dto.UpdateKnowledgeBaseRequest;
import io.github.vfedoriv.graphrag.dto.UpdateKnowledgeBaseAiProfileRequest;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Knowledge Bases", description = "Knowledge base lifecycle operations.")
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/knowledge-bases")
    @Operation(summary = "Create knowledge base", description = "Creates a new knowledge base with a client-defined identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Knowledge base created"),
        @ApiResponse(responseCode = "409", description = "Knowledge base already exists", content = @Content(schema = @Schema()))
    })
    public KnowledgeBaseResponse createKnowledgeBase(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        log.info("Create knowledge base request: knowledgeBaseId={}", request.id());
        KnowledgeBaseResponse response = toResponse(knowledgeBaseService.create(request.id(), request.name()));
        log.info("Create knowledge base completed: knowledgeBaseId={}", response.id());
        return response;
    }

    @GetMapping("/knowledge-bases")
    @Operation(summary = "List knowledge bases", description = "Returns all knowledge bases.")
    @ApiResponse(responseCode = "200", description = "Knowledge bases retrieved")
    public List<KnowledgeBaseResponse> listKnowledgeBases() {
        log.info("List knowledge bases request");
        List<KnowledgeBaseResponse> response = knowledgeBaseService.list().stream().map(this::toResponse).toList();
        log.info("List knowledge bases completed: count={}", response.size());
        return response;
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}")
    @Operation(summary = "Get knowledge base", description = "Returns details for a knowledge base.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Knowledge base found"),
        @ApiResponse(responseCode = "404", description = "Knowledge base not found", content = @Content(schema = @Schema()))
    })
    public KnowledgeBaseResponse getKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId
    ) {
        log.info("Get knowledge base request: knowledgeBaseId={}", knowledgeBaseId);
        KnowledgeBaseResponse response = toResponse(knowledgeBaseService.get(knowledgeBaseId));
        log.info("Get knowledge base completed: knowledgeBaseId={}, activeSchemaId={}", response.id(), response.activeSchemaId());
        return response;
    }

    @PutMapping("/knowledge-bases/{knowledgeBaseId}")
    @Operation(summary = "Update knowledge base", description = "Updates mutable knowledge base metadata.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Knowledge base updated"),
        @ApiResponse(responseCode = "404", description = "Knowledge base not found", content = @Content(schema = @Schema()))
    })
    public KnowledgeBaseResponse updateKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Valid @RequestBody UpdateKnowledgeBaseRequest request
    ) {
        log.info("Update knowledge base request: knowledgeBaseId={}", knowledgeBaseId);
        KnowledgeBaseResponse response = toResponse(knowledgeBaseService.update(knowledgeBaseId, request.name()));
        log.info("Update knowledge base completed: knowledgeBaseId={}", response.id());
        return response;
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/ai-profile")
    @Operation(summary = "Get active AI profile", description = "Returns the AI profile assigned to a knowledge base.")
    public AiProfileResponse getActiveAiProfile(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId
    ) {
        return knowledgeBaseService.getActiveAiProfile(knowledgeBaseId);
    }

    @PutMapping("/knowledge-bases/{knowledgeBaseId}/ai-profile")
    @Operation(summary = "Update active AI profile", description = "Assigns an AI profile to a knowledge base.")
    public KnowledgeBaseResponse updateActiveAiProfile(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Valid @RequestBody UpdateKnowledgeBaseAiProfileRequest request
    ) {
        return toResponse(knowledgeBaseService.updateActiveAiProfile(knowledgeBaseId, request.profileId()));
    }

    @DeleteMapping("/knowledge-bases/{knowledgeBaseId}")
    @Operation(summary = "Delete knowledge base", description = "Deletes a knowledge base and its schema relationship edges.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Knowledge base deleted"),
        @ApiResponse(responseCode = "404", description = "Knowledge base not found", content = @Content(schema = @Schema()))
    })
    public void deleteKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId
    ) {
        log.info("Delete knowledge base request: knowledgeBaseId={}", knowledgeBaseId);
        knowledgeBaseService.delete(knowledgeBaseId);
        log.info("Delete knowledge base completed: knowledgeBaseId={}", knowledgeBaseId);
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBaseNode node) {
        return new KnowledgeBaseResponse(
            node.getId(),
            node.getName(),
            node.getActiveSchemaId(),
            node.getActiveAiProfileId(),
            node.getCreatedAt()
        );
    }
}
