package io.github.vfedoriv.graphrag.documents.domain.chunking;

import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;

public interface TokenEstimator {

    TokenizerId tokenizerId();

    String revision();

    TokenCountMode countMode();

    int count(String text);
}
