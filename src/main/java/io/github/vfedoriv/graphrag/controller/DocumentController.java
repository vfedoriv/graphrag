package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.dto.DocumentChunkResponse;
import io.github.vfedoriv.graphrag.dto.DocumentUploadResponse;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.service.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentChunkRepository documentChunkRepository;

    public DocumentController(
        DocumentUploadService documentUploadService,
        DocumentProcessingService documentProcessingService,
        DocumentChunkRepository documentChunkRepository
    ) {
        this.documentUploadService = documentUploadService;
        this.documentProcessingService = documentProcessingService;
        this.documentChunkRepository = documentChunkRepository;
    }

    @PostMapping(path = "/knowledge-bases/{knowledgeBaseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentUploadResponse uploadDocument(@PathVariable String knowledgeBaseId, @RequestPart("file") MultipartFile file) {
        return toResponse(documentUploadService.upload(knowledgeBaseId, file));
    }

    @PostMapping("/documents/{documentId}/process")
    public DocumentUploadResponse processDocument(@PathVariable String documentId) {
        return toResponse(documentProcessingService.process(documentId));
    }

    @GetMapping("/documents/{documentId}/chunks")
    public List<DocumentChunkResponse> getDocumentChunks(@PathVariable String documentId) {
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
            .map(chunk -> new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getText(),
                chunk.getTokenEstimate(),
                chunk.getMetadata()
            ))
            .toList();
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
