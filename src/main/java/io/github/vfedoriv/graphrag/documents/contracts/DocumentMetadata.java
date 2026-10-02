package io.github.vfedoriv.graphrag.documents.contracts;

/** Citation-safe metadata for an owned document. */
public record DocumentMetadata(String documentId, String filename, String contentType) {
}
