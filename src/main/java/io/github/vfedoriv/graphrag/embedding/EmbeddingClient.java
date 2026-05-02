package io.github.vfedoriv.graphrag.embedding;

import java.util.List;

public interface EmbeddingClient {

    List<List<Double>> embed(List<String> texts);
}
