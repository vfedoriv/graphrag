package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import java.time.Instant;

public record DocumentUploadResponse(
    String id,
    String knowledgeBaseId,
    String originalFilename,
    String contentType,
    long sizeBytes,
    String sha256,
    String contentUri,
    DocumentStatus status,
    Instant uploadedAt,
    Instant processedAt,
    String errorMessage
) {
}
