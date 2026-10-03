package io.github.vfedoriv.graphrag.documents.api.error;

public class GraphExtractionValidationException extends RuntimeException {

    public GraphExtractionValidationException(String message) {
        super(message);
    }
}
