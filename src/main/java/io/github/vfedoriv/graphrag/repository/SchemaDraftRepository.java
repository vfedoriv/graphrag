package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import java.util.List;
import java.util.Optional;

public interface SchemaDraftRepository {
    List<SchemaDraftNode> findByKnowledgeBaseIdOrderByUpdatedAtDesc(String knowledgeBaseId);
    Optional<SchemaDraftNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    Optional<SchemaDraftNode> findById(String id);
    SchemaDraftNode save(SchemaDraftNode draft);
    Long reserveAnalysis(String draftId, String runId, long expectedRevision);
    Long releaseAnalysis(String draftId, String runId);
    Long deleteOwnedGraph(String knowledgeBaseId, String draftId);
}
