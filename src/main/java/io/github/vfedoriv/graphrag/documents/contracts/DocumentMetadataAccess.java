package io.github.vfedoriv.graphrag.documents.contracts;

import java.util.List;

/** Public, bounded metadata reads for consumers that do not own document persistence. */
public interface DocumentMetadataAccess {
    int MAX_BATCH_DOCUMENT_IDS = 128;
    int MAX_SELECTION_RESULTS = 200;

    /** Returns metadata for available documents in the supplied knowledge base, in repository order. */
    List<DocumentMetadata> findOwnedBatch(String knowledgeBaseId, List<String> documentIds);

    /** Returns matching document identifiers in repository order, bounded by {@link #MAX_SELECTION_RESULTS}. */
    List<String> selectOwned(String knowledgeBaseId, String filename, String contentType, int limit);
}
