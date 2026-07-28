package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentStorageMutationRepository {
    List<DocumentStorageMutationNode> findByStateOrderByCreatedAtAsc(DocumentStorageMutationState state);
    Long deleteCompletedBefore(Instant before);
    Optional<DocumentStorageMutationNode> findById(String id);
    DocumentStorageMutationNode save(DocumentStorageMutationNode mutation);
    List<DocumentStorageMutationNode> claimPending(String workerId, Instant now, Instant claimUntil, int limit);
}
