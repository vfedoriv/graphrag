package io.github.vfedoriv.graphrag.schema;

import java.util.List;

public class SchemaValidationException extends RuntimeException {

    private final List<String> errors;

    public SchemaValidationException(List<String> errors) {
        super("Schema validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
