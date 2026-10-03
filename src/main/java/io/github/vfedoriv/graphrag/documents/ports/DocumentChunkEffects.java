package io.github.vfedoriv.graphrag.documents.ports;

import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.domain.chunking.DocumentChunkTopology;
import io.github.vfedoriv.graphrag.documents.domain.chunking.DocumentChunkTopologyAuditRow;
import java.util.List;

public interface DocumentChunkEffects {
    void replace(String documentId, String knowledgeBaseId, EmbeddingTarget target, List<DocumentChunkNode> chunks);
    void prepareRetrievalIndex(String knowledgeBaseId, EmbeddingTarget target);
    List<DocumentChunkNode> findByDocumentId(String documentId);
    DocumentChunkTopology classifyDocumentTopology(String documentId);
    List<DocumentChunkTopologyAuditRow> auditDocumentTopologies();
}
