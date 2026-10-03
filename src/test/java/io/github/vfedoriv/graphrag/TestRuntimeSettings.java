package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;
import org.springframework.mock.env.MockEnvironment;

public final class TestRuntimeSettings {

    private TestRuntimeSettings() {
    }

    public static RuntimeSettingsService from(AppProperties appProperties) {
        return new RuntimeSettingsService(null,io.github.vfedoriv.graphrag.TestRuntimeSettings.startupDefaults(appProperties), AiObservabilityProperties.disabled(), new MockEnvironment(), new io.github.vfedoriv.graphrag.bootstrap.integration.settings.SettingsChunkRevisionAdapter(new io.github.vfedoriv.graphrag.documents.application.inspection.DocumentChunkRevisionsFacade()));
    }

    public static RuntimeSettingsService from(AppProperties appProperties, AiObservabilityProperties observabilityProperties) {
        return new RuntimeSettingsService(null,io.github.vfedoriv.graphrag.TestRuntimeSettings.startupDefaults(appProperties), observabilityProperties, new MockEnvironment(), new io.github.vfedoriv.graphrag.bootstrap.integration.settings.SettingsChunkRevisionAdapter(new io.github.vfedoriv.graphrag.documents.application.inspection.DocumentChunkRevisionsFacade()));
    }
    public static io.github.vfedoriv.graphrag.settings.configuration.SettingsStartupDefaults startupDefaults(AppProperties properties) {
        return new io.github.vfedoriv.graphrag.settings.configuration.SettingsStartupDefaults(properties.query(),
            properties.chunking(), properties.extraction(), properties.storage().documentsRoot().toString(), properties.neo4j().database(),
            new io.github.vfedoriv.graphrag.settings.configuration.SettingsStartupDefaults.ModelDefaults(
                properties.model().baseUrl(), properties.model().apiKey() != null && !properties.model().apiKey().isBlank(),
                properties.model().embeddingModel(), properties.model().embeddingDimensions(), properties.model().chatModel()));
    }

    public static io.github.vfedoriv.graphrag.ai.contracts.StartupModelMetadata modelMetadata(AppProperties properties) {
        return new io.github.vfedoriv.graphrag.ai.contracts.StartupModelMetadata(properties.model().chatModel(), properties.model().embeddingModel());
    }
}
