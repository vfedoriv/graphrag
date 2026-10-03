package io.github.vfedoriv.graphrag.documents.contracts;

/** Document-owned lifecycle facts and scoped artifact cleanup. */
public interface KnowledgeBaseDocuments {
    long ownedCount(String knowledgeBaseId);
    void cleanupArtifacts(String knowledgeBaseId);
}
