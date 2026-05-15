package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.dto.DocumentChunkResponse;
import io.github.vfedoriv.graphrag.dto.DocumentUploadResponse;
import io.github.vfedoriv.graphrag.service.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Documents", description = "Document upload, processing, and chunk retrieval.")
@Slf4j
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final DocumentProcessingService documentProcessingService;

    public DocumentController(
        DocumentUploadService documentUploadService,
        DocumentProcessingService documentProcessingService
    ) {
        this.documentUploadService = documentUploadService;
        this.documentProcessingService = documentProcessingService;
    }

    @PostMapping(path = "/knowledge-bases/{knowledgeBaseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload document", description = "Uploads a document into a knowledge base for later processing.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Document uploaded",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DocumentUploadResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"id\":\"doc-01\",\"knowledgeBaseId\":\"kb-01\",\"originalFilename\":\"contract.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":89432,\"sha256\":\"5f70bf18a086007016e948b04aed3b82\",\"contentUri\":\"file:///var/documents/kb-01/doc-01.pdf\",\"status\":\"UPLOADED\",\"uploadedAt\":\"2026-05-03T10:15:30Z\",\"processedAt\":null,\"errorMessage\":null}"
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid multipart payload", content = @Content(schema = @Schema()))
    })
    public DocumentUploadResponse uploadDocument(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Parameter(description = "Document file to upload") @RequestPart("file") MultipartFile file
    ) {
        log.info(
            "Upload document request: knowledgeBaseId={}, filename={}, contentType={}, sizeBytes={}",
            knowledgeBaseId,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize()
        );
        DocumentUploadResponse response = toResponse(documentUploadService.upload(knowledgeBaseId, file));
        log.info(
            "Upload document completed: knowledgeBaseId={}, documentId={}, status={}",
            knowledgeBaseId,
            response.id(),
            response.status()
        );
        return response;
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    @Operation(summary = "List knowledge base documents", description = "Returns documents uploaded for a specific knowledge base.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Documents retrieved")
    })
    public List<DocumentUploadResponse> listKnowledgeBaseDocuments(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId
    ) {
        log.info("List documents request: knowledgeBaseId={}", knowledgeBaseId);
        List<DocumentUploadResponse> response = documentUploadService.listByKnowledgeBase(knowledgeBaseId).stream()
            .map(this::toResponse)
            .toList();
        log.info("List documents completed: knowledgeBaseId={}, count={}", knowledgeBaseId, response.size());
        return response;
    }

    @PostMapping("/documents/{documentId}/process")
    @Operation(summary = "Process document", description = "Parses, chunks, embeds, and extracts graph data from a previously uploaded document.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Document processed",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DocumentUploadResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"id\":\"doc-01\",\"knowledgeBaseId\":\"kb-01\",\"originalFilename\":\"contract.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":89432,\"sha256\":\"5f70bf18a086007016e948b04aed3b82\",\"contentUri\":\"file:///var/documents/kb-01/doc-01.pdf\",\"status\":\"PROCESSED\",\"uploadedAt\":\"2026-05-03T10:15:30Z\",\"processedAt\":\"2026-05-03T10:16:02Z\",\"errorMessage\":null}"
                )
            )
        ),
        @ApiResponse(responseCode = "409", description = "Completed extraction already exists and overwrite not allowed", content = @Content(schema = @Schema())),
        @ApiResponse(responseCode = "404", description = "Document not found", content = @Content(schema = @Schema()))
    })
    public DocumentUploadResponse processDocument(
        @Parameter(description = "Document identifier") @PathVariable String documentId,
        @Parameter(description = "Allow replacing existing completed extraction for this document")
        @RequestParam(defaultValue = "false") boolean allowOverwrite
    ) {
        log.info("Process document request: documentId={}, allowOverwrite={}", documentId, allowOverwrite);
        DocumentUploadResponse response = toResponse(documentProcessingService.process(documentId, allowOverwrite));
        log.info("Process document completed: documentId={}, status={}", documentId, response.status());
        return response;
    }

    @GetMapping("/documents/{documentId}/chunks")
    @Operation(summary = "List document chunks", description = "Returns chunks generated during processing, ordered by chunk index.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Chunks retrieved"),
        @ApiResponse(responseCode = "404", description = "Document not found", content = @Content(schema = @Schema()))
    })
    public List<DocumentChunkResponse> getDocumentChunks(@Parameter(description = "Document identifier") @PathVariable String documentId) {
        log.info("Get document chunks request: documentId={}", documentId);
        List<DocumentChunkResponse> response = documentProcessingService.getDocumentChunks(documentId).stream()
            .map(chunk -> new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getText(),
                chunk.getTokenEstimate(),
                chunk.getMetadata()
            ))
            .toList();
        log.info("Get document chunks completed: documentId={}, count={}", documentId, response.size());
        return response;
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
