package io.github.vfedoriv.graphrag.observability;

import java.util.Map;

public record AiModelCallContext(
    String operation,
    String workflow,
    String providerProfile,
    String modelName,
    String schemaName,
    Map<String, String> highCardinalityAttributes
) {

    public AiModelCallContext {
        highCardinalityAttributes = highCardinalityAttributes == null ? Map.of() : Map.copyOf(highCardinalityAttributes);
    }
}
