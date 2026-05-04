package io.github.vfedoriv.graphrag.service;

public interface SchemaGenerationService {
    String generateYaml(String name, int version, String description, String text);
}
