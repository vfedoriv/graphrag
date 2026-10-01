package io.github.vfedoriv.graphrag.bootstrap.integration.knowledgebase;

import io.github.vfedoriv.graphrag.documents.contracts.KnowledgeBaseDocuments;
import io.github.vfedoriv.graphrag.knowledgebase.ports.OwnedDocumentState;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseArtifactCleanup;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseDocumentsAdapter implements OwnedDocumentState, KnowledgeBaseArtifactCleanup {
    private final KnowledgeBaseDocuments documents;

    public KnowledgeBaseDocumentsAdapter(KnowledgeBaseDocuments documents) {
        this.documents = documents;
    }

    @Override
    public long countByKnowledgeBaseId(String knowledgeBaseId) {
        return documents.ownedCount(knowledgeBaseId);
    }

    @Override
    public void cleanupKnowledgeBaseArtifacts(String knowledgeBaseId) {
        documents.cleanupArtifacts(knowledgeBaseId);
    }
}
