package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import org.springframework.mock.env.MockEnvironment;

public final class TestRuntimeSettings {

    private TestRuntimeSettings() {
    }

    public static RuntimeSettingsService from(AppProperties appProperties) {
        return new RuntimeSettingsService(null, appProperties, AiObservabilityProperties.disabled(), new MockEnvironment());
    }

    public static RuntimeSettingsService from(AppProperties appProperties, AiObservabilityProperties observabilityProperties) {
        return new RuntimeSettingsService(null, appProperties, observabilityProperties, new MockEnvironment());
    }
}
