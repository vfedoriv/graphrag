package io.github.vfedoriv.graphrag.schemas.discovery.ports;

public interface DiscoveryFileParsing {
    String parse(String filename, String contentType, byte[] bytes);
}
