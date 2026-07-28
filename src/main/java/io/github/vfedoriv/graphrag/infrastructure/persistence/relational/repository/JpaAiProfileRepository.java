package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AiProfileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAiProfileRepository extends JpaRepository<AiProfileEntity, String> {

    List<AiProfileEntity> findAllByOrderByCreatedAtDesc();

    Optional<AiProfileEntity> findFirstByDefaultProfileTrue();

    boolean existsByDefaultProfileTrue();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AiProfileEntity profile SET profile.defaultProfile = false "
        + "WHERE profile.defaultProfile = true AND profile.id <> :profileId")
    int unsetDefaultProfileForOthers(@Param("profileId") String profileId);
}
