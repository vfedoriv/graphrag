package io.github.vfedoriv.graphrag.ai.models;

import java.util.List;

public interface EmbeddingClient {

    List<List<Double>> embed(List<String> texts);
}
