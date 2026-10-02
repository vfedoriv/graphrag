package io.github.vfedoriv.graphrag.search.retrieval.ports;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import java.util.List;
public interface SearchEmbeddingModel {
    Batch embed(String knowledgeBaseId, List<String> texts);
    record Batch(EmbeddingTarget target, List<List<Double>> vectors) {
        public Batch { vectors = vectors.stream().map(List::copyOf).toList(); }
    }
}
