package io.github.vfedoriv.graphrag.knowledgebase.ports;

public interface OwnedDocumentState {
    long countByKnowledgeBaseId(String knowledgeBaseId);
}
