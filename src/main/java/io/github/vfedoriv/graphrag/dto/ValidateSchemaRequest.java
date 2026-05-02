package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidateSchemaRequest(@NotBlank String content) {
}
