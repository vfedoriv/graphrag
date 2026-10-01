package io.github.vfedoriv.graphrag.documents.domain;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    EMBEDDING,
    EXTRACTING_GRAPH,
    COMPLETED,
    FAILED
}
