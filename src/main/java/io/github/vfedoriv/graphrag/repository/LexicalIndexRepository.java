package io.github.vfedoriv.graphrag.repository;

import java.time.Instant;

public interface LexicalIndexRepository {
    String indexName(String knowledgeBaseId);
    String labelName(String knowledgeBaseId);
    void assignChild(String chunkId, String knowledgeBaseId);
    void ensureOnline(String knowledgeBaseId, Instant deadline);
    void drop(String knowledgeBaseId);
}
