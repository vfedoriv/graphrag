package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DocumentUploadResponse(
    @Schema(description = "Document identifier.", example = "doc-01")
    String id,
    @Schema(description = "Knowledge base identifier.", example = "kb-01")
    String knowledgeBaseId,
    @Schema(description = "Original uploaded filename.", example = "contract.pdf")
    String originalFilename,
    @Schema(description = "Document content type.", example = "application/pdf")
    String contentType,
    @Schema(description = "Uploaded file size in bytes.", example = "89432")
    long sizeBytes,
    @Schema(description = "SHA-256 hash of the uploaded binary.", example = "5f70bf18a086007016e948b04aed3b82...")
    String sha256,
    @Schema(description = "Storage URI for the binary.", example = "file:///var/documents/kb-01/doc-01.pdf")
    String contentUri,
    @Schema(description = "Absolute local filesystem path for trusted desktop clients.", example = "/var/documents/kb-01/doc-01.pdf")
    String localPath,
    @Schema(description = "Current document processing status.", example = "UPLOADED")
    DocumentStatus status,
    @Schema(description = "Upload timestamp in UTC.", example = "2026-05-03T10:15:30Z")
    Instant uploadedAt,
    @Schema(description = "Processing completion timestamp in UTC, when available.", example = "2026-05-03T10:16:02Z")
    Instant processedAt,
    @Schema(description = "Processing error message, if any.", example = "Unsupported file format")
    String errorMessage
) {
}
