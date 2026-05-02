package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.dto.DocumentUploadResponse;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentUploadService documentUploadService;

    public DocumentController(DocumentUploadService documentUploadService) {
        this.documentUploadService = documentUploadService;
    }

    @PostMapping(path = "/knowledge-bases/{knowledgeBaseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentUploadResponse uploadDocument(@PathVariable String knowledgeBaseId, @RequestPart("file") MultipartFile file) {
        return toResponse(documentUploadService.upload(knowledgeBaseId, file));
    }

    private DocumentUploadResponse toResponse(DocumentUploadNode node) {
        return new DocumentUploadResponse(
            node.getId(),
            node.getKnowledgeBaseId(),
            node.getOriginalFilename(),
            node.getContentType(),
            node.getSizeBytes(),
            node.getSha256(),
            node.getContentUri(),
            node.getStatus(),
            node.getUploadedAt(),
            node.getProcessedAt(),
            node.getErrorMessage()
        );
    }
}
