package io.github.vfedoriv.graphrag.schemas.reprocessing.ports;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Read-only document facts; selection policy and durable plan assembly belong to schemas. */
public interface ReprocessingDocumentPreparation {
    List<Summary> allOwned(String knowledgeBaseId);
    List<Summary> ownedIds(String knowledgeBaseId, List<String> documentIds);
    String targetRevision(Profile profile);
    Identity identity(Profile profile);
    Inspection inspect(String knowledgeBaseId, Profile profile);
    List<Prepared> prepare(List<Summary> documents, Profile profile, Map<String, Object> options);

    /** Captured non-secret embedding/tokenizer inputs, without a second profile lookup. */
    record Profile(String id, long revision, String baseUrl, String embeddingModel,
                   int embeddingDimensions, String tokenizerId) { }

    /** Source metadata needed by the existing option resolver, with no persistence/runtime objects. */
    record Summary(String id, String originalFilename, String sha256, Instant uploadedAt,
                   String status, String contentType, String processingDefaultsJson) { }

    record Identity(String embeddingSpaceId, String targetRevision) { }
    record Blocker(String code, String message) { }
    record Inspection(String targetRevision, String embeddingSpaceId,
                      ReprocessingDocumentExecutor.ChunkTarget chunkTarget, Blocker blocker) { }
    enum Classification { NO_CHUNKS, CURRENT, OUTDATED }
    record Prepared(Summary document, Classification classification,
                    ReprocessingDocumentExecutor.DocumentTarget target) { }
}
