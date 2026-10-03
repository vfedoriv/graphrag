package io.github.vfedoriv.graphrag.documents.api.error;

import java.util.List;

public class ProcessingOptionsValidationException extends RuntimeException {

    private final List<String> errors;

    public ProcessingOptionsValidationException(List<String> errors) {
        super("Document processing options validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
