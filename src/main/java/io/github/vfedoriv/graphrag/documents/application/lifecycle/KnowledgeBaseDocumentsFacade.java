package io.github.vfedoriv.graphrag.documents.application.lifecycle;

import io.github.vfedoriv.graphrag.documents.contracts.KnowledgeBaseDocuments;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentArtifactCleanup;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseDocumentsFacade implements KnowledgeBaseDocuments {
    private final DocumentUploadRepository documents;
    private final DocumentArtifactCleanup cleanup;

    public KnowledgeBaseDocumentsFacade(DocumentUploadRepository documents, DocumentArtifactCleanup cleanup) {
        this.documents = documents;
        this.cleanup = cleanup;
    }

    @Override
    public long ownedCount(String knowledgeBaseId) {
        return documents.countByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public void cleanupArtifacts(String knowledgeBaseId) {
        cleanup.cleanupKnowledgeBaseArtifacts(knowledgeBaseId);
    }
}
