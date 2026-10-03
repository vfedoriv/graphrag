package io.github.vfedoriv.graphrag.knowledgebase.ports;

import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {
    List<KnowledgeBaseNode> findAllByOrderByCreatedAtDesc();

    Optional<KnowledgeBaseNode> findById(String id);

    boolean existsById(String id);

    KnowledgeBaseNode save(KnowledgeBaseNode knowledgeBase);

    void deleteById(String id);

    boolean assignAiProfile(String id, long expectedVersion, String profileId);

    Boolean existsAiProfileAssignment(String profileId);

    List<String> findIdsByActiveAiProfileId(String profileId);
}
