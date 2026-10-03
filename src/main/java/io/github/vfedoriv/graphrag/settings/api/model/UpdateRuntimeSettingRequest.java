package io.github.vfedoriv.graphrag.settings.api.model;

import jakarta.validation.constraints.NotNull;

public record UpdateRuntimeSettingRequest(
    @NotNull Object value
) {
}
