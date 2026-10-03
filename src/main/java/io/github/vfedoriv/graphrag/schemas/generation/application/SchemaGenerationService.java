package io.github.vfedoriv.graphrag.schemas.generation.application;

import io.github.vfedoriv.graphrag.schemas.generation.domain.SchemaGenerationResult;

public interface SchemaGenerationService {
    SchemaGenerationResult generate(String name, int version, String description, String text, String example);

    default SchemaGenerationResult generate(String knowledgeBaseId, String name, int version, String description, String text, String example) {
        return generate(name, version, description, text, example);
    }

    default String generateJson(String name, int version, String description, String text, String example) {
        return generate(name, version, description, text, example).content();
    }

    String generateExample(String text, String userPrompt);

    default String generateExample(String knowledgeBaseId, String text, String userPrompt) {
        return generateExample(text, userPrompt);
    }
}
