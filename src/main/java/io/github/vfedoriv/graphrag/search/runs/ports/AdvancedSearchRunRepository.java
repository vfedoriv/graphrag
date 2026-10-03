package io.github.vfedoriv.graphrag.search.runs.ports;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunNode;
import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdvancedSearchRunRepository {
    AdvancedSearchRunNode save(AdvancedSearchRunNode run);
    Optional<AdvancedSearchRunNode> findById(String id);
    Optional<AdvancedSearchRunNode> findOwned(String id, String knowledgeBaseId);
    Page<AdvancedSearchRunNode> findOwnedPage(String knowledgeBaseId, AdvancedSearchRunStatus status, Pageable pageable);
    List<AdvancedSearchRunNode> findActive();
    boolean claim(String runId, String workerId, Instant now);
    boolean cancelQueued(String runId, String knowledgeBaseId, Instant now, Instant expiresAt);
    boolean requestRunningCancellation(String runId, String knowledgeBaseId, Instant now);
    int deleteExpired(Instant now, int batchSize);
}
