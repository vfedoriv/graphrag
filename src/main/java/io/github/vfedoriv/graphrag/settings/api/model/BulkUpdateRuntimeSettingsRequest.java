package io.github.vfedoriv.graphrag.settings.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkUpdateRuntimeSettingsRequest(
    @NotEmpty List<@Valid RuntimeSettingUpdateRequest> updates
) {
}
