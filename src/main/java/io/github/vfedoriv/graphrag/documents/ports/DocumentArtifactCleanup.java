package io.github.vfedoriv.graphrag.documents.ports;

import java.util.List;

public interface DocumentArtifactCleanup {
    DocumentArtifactCleanupResult cleanupDocumentArtifacts(String documentId);
    DocumentArtifactCleanupResult cleanupKnowledgeBaseArtifacts(String knowledgeBaseId);
    ExtractionRunCleanupResult cleanupRunsAfterSuccessfulExtraction(String documentId, String runId, boolean allowOverwrite);
    ExtractionRunCleanupResult cleanupExtractionRuns(String documentId, List<String> runIds);

    public record DocumentArtifactCleanupResult(
        long deletedChunks,
        long deletedProcessingRuns,
        long deletedRuns,
        long deletedGraphEvidence,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        public static DocumentArtifactCleanupResult zero() {
            return new DocumentArtifactCleanupResult(0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public record ExtractionRunCleanupResult(
        long deletedRuns,
        long deletedGraphEvidence,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        public static ExtractionRunCleanupResult zero() {
            return new ExtractionRunCleanupResult(0L, 0L, 0L, 0L);
        }
    }

}
