package io.github.vfedoriv.graphrag.error;

public class AdvancedSearchCapacityException extends RuntimeException {
    public AdvancedSearchCapacityException() { super("Advanced search capacity is exhausted"); }
}
