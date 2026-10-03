package io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.repository;

import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity.KnowledgeBaseEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaKnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, String> {
    List<KnowledgeBaseEntity> findAllByOrderByCreatedAtDesc();

    boolean existsByActiveAiProfileId(String profileId);

    List<KnowledgeBaseEntity> findAllByActiveAiProfileId(String profileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT knowledgeBase FROM KnowledgeBaseEntity knowledgeBase WHERE knowledgeBase.id = :id")
    Optional<KnowledgeBaseEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE KnowledgeBaseEntity knowledgeBase
        SET knowledgeBase.activeAiProfileId = :profileId,
            knowledgeBase.updatedAt = CURRENT_TIMESTAMP,
            knowledgeBase.version = knowledgeBase.version + 1
        WHERE knowledgeBase.id = :id AND knowledgeBase.version = :expectedVersion
        """)
    int assignAiProfile(
        @Param("id") String id,
        @Param("expectedVersion") long expectedVersion,
        @Param("profileId") String profileId
    );
}
