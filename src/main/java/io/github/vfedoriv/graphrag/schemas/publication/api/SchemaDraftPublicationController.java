package io.github.vfedoriv.graphrag.schemas.publication.api;

import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.PublicationReadinessResponse;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.PublishDraftRequest;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.PublicationResponse;
import io.github.vfedoriv.graphrag.schemas.publication.application.SchemaDraftPublicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts")
public class SchemaDraftPublicationController {
    private final SchemaDraftPublicationService publicationService;
    public SchemaDraftPublicationController(SchemaDraftPublicationService publicationService) { this.publicationService = publicationService; }
    @GetMapping("/{draftId}/publication-readiness")
    public PublicationReadinessResponse publicationReadiness(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId
    ) {
        return publicationService.readiness(knowledgeBaseId, draftId);
    }

    @PostMapping("/{draftId}/publish")
    public PublicationResponse publish(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId,
        @Valid @RequestBody PublishDraftRequest request
    ) {
        return publicationService.publish(knowledgeBaseId, draftId, request);
    }

    @GetMapping("/{draftId}/publication")
    public PublicationResponse publication(
        @PathVariable String knowledgeBaseId, @PathVariable String draftId
    ) {
        return publicationService.get(knowledgeBaseId, draftId);
    }
}
