package io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.repository;

import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity.KnowledgeBaseSchemaEntity;
import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity.KnowledgeBaseSchemaId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaKnowledgeBaseSchemaRepository
    extends JpaRepository<KnowledgeBaseSchemaEntity, KnowledgeBaseSchemaId> {

    List<KnowledgeBaseSchemaEntity> findAllByKnowledgeBaseId(String knowledgeBaseId);

    Optional<KnowledgeBaseSchemaEntity> findFirstByKnowledgeBaseIdAndActiveTrue(String knowledgeBaseId);

    boolean existsBySchemaIdAndActiveTrue(String schemaId);

    long deleteBySchemaId(String schemaId);

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE KnowledgeBaseSchemaEntity association
        SET association.active = false,
            association.updatedAt = CURRENT_TIMESTAMP,
            association.version = association.version + 1
        WHERE association.knowledgeBaseId = :knowledgeBaseId AND association.active = true
        """)
    int deactivateAll(@Param("knowledgeBaseId") String knowledgeBaseId);
}
