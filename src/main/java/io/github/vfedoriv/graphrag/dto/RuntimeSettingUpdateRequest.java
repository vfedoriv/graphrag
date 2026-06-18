package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RuntimeSettingUpdateRequest(
    @NotBlank String key,
    @NotNull Object value
) {
}
