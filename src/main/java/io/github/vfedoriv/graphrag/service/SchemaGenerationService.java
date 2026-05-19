package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.dto.SchemaGenerationResult;

public interface SchemaGenerationService {
    SchemaGenerationResult generate(String name, int version, String description, String text, String example);

    default String generateJson(String name, int version, String description, String text, String example) {
        return generate(name, version, description, text, example).content();
    }

    String generateExample(String text, String userPrompt);
}
