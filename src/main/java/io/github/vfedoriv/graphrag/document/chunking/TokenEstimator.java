package io.github.vfedoriv.graphrag.document.chunking;

public interface TokenEstimator {

    TokenizerId tokenizerId();

    String revision();

    TokenCountMode countMode();

    int count(String text);
}
