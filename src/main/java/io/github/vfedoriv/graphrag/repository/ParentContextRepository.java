package io.github.vfedoriv.graphrag.repository;

import java.util.List;

public interface ParentContextRepository {

    List<ParentContextRow> load(
        String knowledgeBaseId,
        List<ParentContextCandidate> candidates,
        int adjacentChunks
    );

    record ParentContextCandidate(
        int rank,
        String chunkId,
        String documentId,
        String processingRunId,
        String strategyRevision
    ) {
    }

    record ParentContextRow(
        int rank,
        String childId,
        String documentId,
        String strategyRevision,
        String outcome,
        String parentId,
        String parentText,
        Integer parentTokenEstimate,
        Integer parentSourceStart,
        Integer parentSourceEnd,
        Integer parentPageStart,
        Integer parentPageEnd,
        List<AdjacentChunk> adjacency
    ) {
        public ParentContextRow {
            adjacency = adjacency == null ? List.of() : List.copyOf(adjacency);
        }
    }

    record AdjacentChunk(
        String id,
        String text,
        Integer tokenEstimate,
        Integer sourceStart,
        Integer sourceEnd,
        Integer pageStart,
        Integer pageEnd
    ) {
    }
}
