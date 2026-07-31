package io.github.vfedoriv.graphrag.service;

public final class LexicalIndexIdentity {
    private static final String INDEX_PREFIX = "document_chunk_source_text_";
    private static final String LABEL_PREFIX = "KnowledgeBaseText_";

    private LexicalIndexIdentity() { }

    public static String indexName(String knowledgeBaseId) {
        return INDEX_PREFIX + partitionHash(knowledgeBaseId);
    }

    public static String labelName(String knowledgeBaseId) {
        return LABEL_PREFIX + partitionHash(knowledgeBaseId);
    }

    private static String partitionHash(String knowledgeBaseId) {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
        return EmbeddingSpaceIdentity.sha256(knowledgeBaseId).substring(0, 24);
    }
}
