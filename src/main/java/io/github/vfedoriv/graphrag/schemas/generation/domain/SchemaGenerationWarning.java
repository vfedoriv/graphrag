package io.github.vfedoriv.graphrag.schemas.generation.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SchemaGenerationWarning(
    @Schema(description = "Node index in generated schema `nodes` array.", example = "0")
    int nodeIndex,
    @Schema(description = "Generated node label.", example = "Person")
    String nodeLabel,
    @Schema(description = "Stable warning code.", example = "NODE_KEY_PROPERTY_MISMATCH")
    String code,
    @Schema(
        description = "Human-readable warning message.",
        example = "Node key 'id' is not declared in properties for node 'Person'"
    )
    String message,
    @Schema(
        description = "Actionable suggestions to resolve the warning.",
        example = "[\"Add property 'id' to node properties\", \"Change node key to an existing property name\"]"
    )
    List<String> suggestions
) {
}
