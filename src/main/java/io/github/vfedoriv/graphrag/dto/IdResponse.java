package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.NotBlank;

public record IdResponse(@NotBlank String id) {
}
