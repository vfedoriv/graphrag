package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRuntimeSettingRequest(
    @NotNull Object value
) {
}
