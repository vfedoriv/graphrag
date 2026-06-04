package io.github.vfedoriv.graphrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
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
    void defaultProfileDisablesAiObservabilityExport() throws IOException {
        Properties properties = load("src/main/resources/application.properties");

        assertThat(properties.getProperty("app.ai.observability.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("app.ai.observability.content-capture-enabled")).isEqualTo("false");
        assertThat(properties.getProperty("app.ai.observability.input-output-content-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("app.ai.observability.max-input-output-length")).isEqualTo("1048576");
        assertThat(properties.getProperty("management.tracing.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("management.tracing.export.otlp.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("management.opentelemetry.tracing.export.otlp.endpoint")).isEmpty();
    }

    @Test
    void aiProfilesExplicitlySelectOpenAiModelProvider() throws IOException {
        assertOpenAiProviderSelected("src/main/resources/application-openai.properties");
        assertOpenAiProviderSelected("src/main/resources/application-lm_studio.properties");
    }

    @Test
    void langfuseProfileEnablesTracingExportToLocalOtlpHttpEndpoint() throws IOException {
        Properties properties = load("src/main/resources/application-langfuse.properties");

        assertThat(properties.getProperty("app.ai.observability.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("app.ai.observability.input-output-content-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("app.ai.observability.max-input-output-length")).isEqualTo("1048576");
        assertThat(properties.getProperty("management.tracing.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("management.tracing.export.otlp.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
            .isEqualTo("http://localhost:3000/api/public/otel/v1/traces");
        assertThat(properties.getProperty("management.opentelemetry.tracing.export.otlp.headers.Authorization"))
            .isEqualTo("Basic cGstbGYtbG9jYWwtZGV2OnNrLWxmLWxvY2FsLWRldg==");
        assertThat(properties.getProperty("management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version"))
            .isEqualTo("4");
    }

    private void assertOpenAiProviderSelected(String path) throws IOException {
        Properties properties = load(path);

        assertThat(properties.getProperty("spring.ai.model.chat")).isEqualTo("openai");
        assertThat(properties.getProperty("spring.ai.model.embedding")).isEqualTo("openai");
    }

    private Properties load(String path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
            properties.load(reader);
        }
        return properties;
    }
}
