package io.github.vfedoriv.graphrag.search.runs.api.error;

public class AdvancedSearchCapacityException extends RuntimeException {
    public AdvancedSearchCapacityException() { super("Advanced search capacity is exhausted"); }
}
