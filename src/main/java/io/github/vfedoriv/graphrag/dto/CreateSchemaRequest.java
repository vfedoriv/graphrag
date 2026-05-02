package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import jakarta.validation.constraints.NotBlank;

public record CreateSchemaRequest(
    @NotBlank String content,
    SchemaSourceType sourceType
) {
}
