package io.github.vfedoriv.graphrag.service;

import java.util.List;

public interface SchemaGenerationService {
    SchemaGenerationResult generate(String name, int version, String description, String text, String example);

    default String generateJson(String name, int version, String description, String text, String example) {
        return generate(name, version, description, text, example).content();
    }

    String generateExample(String text, String userPrompt);

    record SchemaGenerationResult(
        String content,
        List<SchemaGenerationWarning> warnings
    ) {
    }

    record SchemaGenerationWarning(
        int nodeIndex,
        String nodeLabel,
        String code,
        String message,
        List<String> suggestions
    ) {
    }
}
