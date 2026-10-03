package io.github.vfedoriv.graphrag.settings.api;

import io.github.vfedoriv.graphrag.settings.api.model.BulkUpdateRuntimeSettingsRequest;
import io.github.vfedoriv.graphrag.settings.api.model.RuntimeSettingResponse;
import io.github.vfedoriv.graphrag.settings.api.model.UpdateRuntimeSettingRequest;
import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runtime-settings")
public class RuntimeSettingsController {

    private final RuntimeSettingsService runtimeSettingsService;

    public RuntimeSettingsController(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @GetMapping
    public List<RuntimeSettingResponse> listSettings() {
        return runtimeSettingsService.list();
    }

    @PutMapping("/{key}")
    public RuntimeSettingResponse updateSetting(
        @PathVariable String key,
        @Valid @RequestBody UpdateRuntimeSettingRequest request
    ) {
        return runtimeSettingsService.update(key, request.value());
    }

    @PutMapping
    public List<RuntimeSettingResponse> updateSettings(@Valid @RequestBody BulkUpdateRuntimeSettingsRequest request) {
        return runtimeSettingsService.update(request.updates());
    }

    @DeleteMapping("/{key}")
    public RuntimeSettingResponse clearSetting(@PathVariable String key) {
        return runtimeSettingsService.clear(key);
    }
}
