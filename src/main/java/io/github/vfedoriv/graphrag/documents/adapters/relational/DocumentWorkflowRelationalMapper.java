package io.github.vfedoriv.graphrag.documents.adapters.relational;

import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentProcessingRunEntity;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentStorageMutationEntity;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentUploadEntity;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.ExtractionRunEntity;

final class DocumentWorkflowRelationalMapper {
    private DocumentWorkflowRelationalMapper() {
    }

    static DocumentUploadEntity toEntity(DocumentUploadNode source) {
        DocumentUploadEntity target = new DocumentUploadEntity();
        target.setId(source.getId());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setOriginalFilename(source.getOriginalFilename());
        target.setContentType(source.getContentType());
        target.setSizeBytes(source.getSizeBytes());
        target.setSha256(source.getSha256());
        target.setContentUri(source.getContentUri());
        target.setStatus(source.getStatus());
        target.setUploadedAt(source.getUploadedAt());
        target.setProcessedAt(source.getProcessedAt());
        target.setErrorMessage(source.getErrorMessage());
        target.setProcessingDefaultsJson(source.getProcessingDefaultsJson());
        target.setProcessingDefaultsUpdatedAt(source.getProcessingDefaultsUpdatedAt());
        target.setVersion(source.getVersion());
        return target;
    }

    static DocumentUploadNode toDomain(DocumentUploadEntity source) {
        DocumentUploadNode target = new DocumentUploadNode();
        target.setId(source.getId());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setOriginalFilename(source.getOriginalFilename());
        target.setContentType(source.getContentType());
        target.setSizeBytes(source.getSizeBytes());
        target.setSha256(source.getSha256());
        target.setContentUri(source.getContentUri());
        target.setStatus(source.getStatus());
        target.setUploadedAt(source.getUploadedAt());
        target.setProcessedAt(source.getProcessedAt());
        target.setErrorMessage(source.getErrorMessage());
        target.setProcessingDefaultsJson(source.getProcessingDefaultsJson());
        target.setProcessingDefaultsUpdatedAt(source.getProcessingDefaultsUpdatedAt());
        target.setVersion(source.getVersion());
        return target;
    }

    static DocumentStorageMutationEntity toEntity(DocumentStorageMutationNode source) {
        DocumentStorageMutationEntity target = new DocumentStorageMutationEntity();
        target.setId(source.getId());
        target.setType(source.getType());
        target.setState(source.getState());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setDocumentId(source.getDocumentId());
        target.setContentUri(source.getContentUri());
        target.setPreviousContentUri(source.getPreviousContentUri());
        target.setRetryCount(source.getRetryCount());
        target.setLastError(source.getLastError());
        target.setCreatedAt(source.getCreatedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setClaimedBy(source.getClaimedBy());
        target.setClaimUntil(source.getClaimUntil());
        target.setVersion(source.getVersion());
        return target;
    }

    static DocumentStorageMutationNode toDomain(DocumentStorageMutationEntity source) {
        DocumentStorageMutationNode target = new DocumentStorageMutationNode();
        target.setId(source.getId());
        target.setType(source.getType());
        target.setState(source.getState());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setDocumentId(source.getDocumentId());
        target.setContentUri(source.getContentUri());
        target.setPreviousContentUri(source.getPreviousContentUri());
        target.setRetryCount(source.getRetryCount());
        target.setLastError(source.getLastError());
        target.setCreatedAt(source.getCreatedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setClaimedBy(source.getClaimedBy());
        target.setClaimUntil(source.getClaimUntil());
        target.setVersion(source.getVersion());
        return target;
    }

    static DocumentProcessingRunEntity toEntity(DocumentProcessingRunNode source) {
        DocumentProcessingRunEntity target = new DocumentProcessingRunEntity();
        target.setId(source.getId());
        target.setDocumentId(source.getDocumentId());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setSourceSha256(source.getSourceSha256());
        target.setParserId(source.getParserId());
        target.setFileFormat(source.getFileFormat());
        target.setRequestedOptionsJson(source.getRequestedOptionsJson());
        target.setSavedDefaultsJson(source.getSavedDefaultsJson());
        target.setEffectiveOptionsJson(source.getEffectiveOptionsJson());
        target.setChunkStrategy(source.getChunkStrategy());
        target.setChunkStrategyRevision(source.getChunkStrategyRevision());
        target.setChunkSettingsHash(source.getChunkSettingsHash());
        target.setTokenizerId(source.getTokenizerId());
        target.setTokenizerRevision(source.getTokenizerRevision());
        target.setTokenCountMode(source.getTokenCountMode());
        target.setEffectiveChunkerRevision(source.getEffectiveChunkerRevision());
        target.setStatus(source.getStatus());
        target.setStage(source.getStage());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setErrorMessage(source.getErrorMessage());
        target.setActiveCompleted(source.isActiveCompleted());
        target.setRetryCount(source.getRetryCount());
        target.setRetryOfRunId(source.getRetryOfRunId());
        target.setClaimedBy(source.getClaimedBy());
        target.setClaimUntil(source.getClaimUntil());
        target.setVersion(source.getVersion());
        return target;
    }

    static DocumentProcessingRunNode toDomain(DocumentProcessingRunEntity source) {
        DocumentProcessingRunNode target = new DocumentProcessingRunNode();
        target.setId(source.getId());
        target.setDocumentId(source.getDocumentId());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setSourceSha256(source.getSourceSha256());
        target.setParserId(source.getParserId());
        target.setFileFormat(source.getFileFormat());
        target.setRequestedOptionsJson(source.getRequestedOptionsJson());
        target.setSavedDefaultsJson(source.getSavedDefaultsJson());
        target.setEffectiveOptionsJson(source.getEffectiveOptionsJson());
        target.setChunkStrategy(source.getChunkStrategy());
        target.setChunkStrategyRevision(source.getChunkStrategyRevision());
        target.setChunkSettingsHash(source.getChunkSettingsHash());
        target.setTokenizerId(source.getTokenizerId());
        target.setTokenizerRevision(source.getTokenizerRevision());
        target.setTokenCountMode(source.getTokenCountMode());
        target.setEffectiveChunkerRevision(source.getEffectiveChunkerRevision());
        target.setStatus(source.getStatus());
        target.setStage(source.getStage());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setErrorMessage(source.getErrorMessage());
        target.setActiveCompleted(source.isActiveCompleted());
        target.setRetryCount(source.getRetryCount());
        target.setRetryOfRunId(source.getRetryOfRunId());
        target.setClaimedBy(source.getClaimedBy());
        target.setClaimUntil(source.getClaimUntil());
        target.setVersion(source.getVersion());
        return target;
    }

    static ExtractionRunEntity toEntity(ExtractionRunNode source) {
        ExtractionRunEntity target = new ExtractionRunEntity();
        target.setId(source.getId());
        target.setDocumentId(source.getDocumentId());
        target.setSchemaId(source.getSchemaId());
        target.setModel(source.getModel());
        target.setStatus(source.getStatus());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setErrorMessage(source.getErrorMessage());
        target.setRetryCount(source.getRetryCount());
        target.setRetryOfRunId(source.getRetryOfRunId());
        target.setClaimedBy(source.getClaimedBy());
        target.setClaimUntil(source.getClaimUntil());
        target.setVersion(source.getVersion());
        return target;
    }

    static ExtractionRunNode toDomain(ExtractionRunEntity source) {
        ExtractionRunNode target = new ExtractionRunNode();
        target.setId(source.getId());
        target.setDocumentId(source.getDocumentId());
        target.setSchemaId(source.getSchemaId());
        target.setModel(source.getModel());
        target.setStatus(source.getStatus());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setErrorMessage(source.getErrorMessage());
        target.setRetryCount(source.getRetryCount());
        target.setRetryOfRunId(source.getRetryOfRunId());
        target.setClaimedBy(source.getClaimedBy());
        target.setClaimUntil(source.getClaimUntil());
        target.setVersion(source.getVersion());
        return target;
    }
}
