package io.github.vfedoriv.graphrag.observability;

import java.util.Map;

public record AiWorkflowContext(
    String workflow,
    String schemaName,
    Map<String, String> highCardinalityAttributes
) {

    public AiWorkflowContext {
        highCardinalityAttributes = highCardinalityAttributes == null ? Map.of() : Map.copyOf(highCardinalityAttributes);
    }
}
