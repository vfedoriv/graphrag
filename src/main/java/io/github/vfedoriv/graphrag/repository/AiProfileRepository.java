package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import java.util.List;
import java.util.Optional;

public interface AiProfileRepository {

    List<AiProfileNode> findAllByOrderByCreatedAtDesc();

    Optional<AiProfileNode> findFirstByDefaultProfileTrue();

    Boolean existsByDefaultProfileTrue();

    Optional<AiProfileNode> findById(String id);

    boolean existsById(String id);

    AiProfileNode save(AiProfileNode profile);

    void deleteById(String id);

    Long unsetDefaultProfileForOthers(String profileId);

}
