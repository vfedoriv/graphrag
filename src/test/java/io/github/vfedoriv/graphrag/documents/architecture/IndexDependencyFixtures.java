package io.github.vfedoriv.graphrag.documents.architecture;

import io.github.vfedoriv.graphrag.indexes.adapters.graph.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.indexes.contracts.VectorIndexes;

public final class IndexDependencyFixtures {
    private IndexDependencyFixtures() { }
    public record ForbiddenImplementation(EmbeddingSpaceIndexService indexes) { }
    public record AllowedContract(VectorIndexes indexes) { }
}
