package io.github.vfedoriv.graphrag.error;

import java.util.List;

public class QueryRejectedException extends RuntimeException {

    private final List<String> errors;

    public QueryRejectedException(List<String> errors) {
        super("Query validation failed");
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
