package io.github.vfedoriv.graphrag.documents.domain.chunking;

public interface TokenEstimator {

    TokenizerId tokenizerId();

    String revision();

    TokenCountMode countMode();

    int count(String text);
}
