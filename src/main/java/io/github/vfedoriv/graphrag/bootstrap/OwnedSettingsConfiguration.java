package io.github.vfedoriv.graphrag.bootstrap;

import io.github.vfedoriv.graphrag.ai.contracts.StartupModelMetadata;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.settings.configuration.SettingsStartupDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OwnedSettingsConfiguration {
    @Bean
    SettingsStartupDefaults settingsStartupDefaults(AppProperties properties) {
        return new SettingsStartupDefaults(properties.query(), properties.chunking(), properties.extraction(),
            properties.storage().documentsRoot().toString(), properties.neo4j().database(),
            new io.github.vfedoriv.graphrag.settings.configuration.SettingsStartupDefaults.ModelDefaults(
                properties.model().baseUrl(), properties.model().apiKey() != null && !properties.model().apiKey().isBlank(),
                properties.model().embeddingModel(), properties.model().embeddingDimensions(), properties.model().chatModel()));
    }

    @Bean
    StartupModelMetadata startupModelMetadata(ModelProperties model) {
        return new StartupModelMetadata(model.chatModel(), model.embeddingModel());
    }
}
