package io.github.vfedoriv.graphrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AiModelProfilePropertiesTest {

    @Test
    void defaultProfileDisablesSpringAiModelAutoConfiguration() throws IOException {
        Properties properties = load("src/main/resources/application.properties");

        assertThat(properties.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(properties.getProperty("spring.ai.model.embedding")).isEqualTo("none");
    }

    @Test
    void aiProfilesExplicitlySelectOpenAiModelProvider() throws IOException {
        assertOpenAiProviderSelected("src/main/resources/application-openai.properties");
        assertOpenAiProviderSelected("src/main/resources/application-lm_studio.properties");
    }

    private void assertOpenAiProviderSelected(String path) throws IOException {
        Properties properties = load(path);

        assertThat(properties.getProperty("spring.ai.model.chat")).isEqualTo("openai");
        assertThat(properties.getProperty("spring.ai.model.embedding")).isEqualTo("openai");
    }

    private Properties load(String path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(path))) {
            properties.load(reader);
        }
        return properties;
    }
}
