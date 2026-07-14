package io.github.vfedoriv.graphrag.error;

public class SchemaDiscoveryFailedException extends RuntimeException {
    public SchemaDiscoveryFailedException(String message) {
        super(message);
    }
}
