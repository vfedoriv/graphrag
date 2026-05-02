package io.github.vfedoriv.graphrag.domain;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    EMBEDDING,
    EXTRACTING_GRAPH,
    COMPLETED,
    FAILED
}
