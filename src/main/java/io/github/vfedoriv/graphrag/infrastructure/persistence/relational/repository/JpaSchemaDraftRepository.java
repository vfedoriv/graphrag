package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftEntity;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSchemaDraftRepository extends JpaRepository<SchemaDraftEntity, String> {
    List<SchemaDraftEntity> findByKnowledgeBaseIdOrderByUpdatedAtDescIdDesc(String knowledgeBaseId);
    Optional<SchemaDraftEntity> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaDraftEntity draft
        set draft.runningAnalysisRunId = :runId,
            draft.persistenceVersion = draft.persistenceVersion + 1
        where draft.id = :draftId
          and draft.status = io.github.vfedoriv.graphrag.domain.SchemaDraftStatus.OPEN
          and draft.runningAnalysisRunId is null
          and draft.revision = :expectedRevision
        """)
    int reserveAnalysis(
        @Param("draftId") String draftId,
        @Param("runId") String runId,
        @Param("expectedRevision") long expectedRevision
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaDraftEntity draft
        set draft.runningAnalysisRunId = null,
            draft.persistenceVersion = draft.persistenceVersion + 1
        where draft.id = :draftId and draft.runningAnalysisRunId = :runId
        """)
    int releaseAnalysis(@Param("draftId") String draftId, @Param("runId") String runId);

    long deleteByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
}
