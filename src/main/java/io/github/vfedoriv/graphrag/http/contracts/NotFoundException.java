package io.github.vfedoriv.graphrag.http.contracts;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
