package io.github.vfedoriv.graphrag.schemas.discovery.api.error;

public class SchemaDiscoveryFailedException extends RuntimeException {
    public SchemaDiscoveryFailedException(String message) {
        super(message);
    }
}
