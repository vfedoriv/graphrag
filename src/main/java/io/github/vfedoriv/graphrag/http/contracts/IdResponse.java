package io.github.vfedoriv.graphrag.http.contracts;

import jakarta.validation.constraints.NotBlank;

public record IdResponse(@NotBlank String id) {
}
