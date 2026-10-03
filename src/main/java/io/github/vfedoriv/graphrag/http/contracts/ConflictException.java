package io.github.vfedoriv.graphrag.http.contracts;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
