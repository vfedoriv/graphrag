package io.github.vfedoriv.graphrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class AppPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class,
                ValidationAutoConfiguration.class
            )
        )
        .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsPropertiesSuccessfully() {
        contextRunner
            .withPropertyValues(
                "app.neo4j.database=neo4j",
                "app.model.base-url=https://api.openai.com/v1",
                "app.model.embedding-model=text-embedding-3-small",
                "app.model.embedding-dimensions=1536",
                "app.model.chat-model=gpt-5-mini",
                "app.storage.documents-root=./var/documents",
                "app.chunking.strategy=fixed-character",
                "app.chunking.target-tokens=700",
                "app.chunking.max-tokens=800",
                "app.chunking.overlap-tokens=80",
                "app.chunking.hard-character-limit=3500",
                "app.chunking.max-characters=4000",
                "app.query.max-rows=200",
                "app.query.timeout-seconds=15",
                "app.query.require-limit=true",
                "app.query.blocked-keywords=CREATE,MERGE",
                "app.extraction.max-entities-per-chunk=40",
                "app.extraction.max-relationships-per-chunk=80",
                "app.extraction.max-retries=2"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                AppProperties properties = context.getBean(AppProperties.class);
                assertThat(properties.model().chatModel()).isEqualTo("gpt-5-mini");
                assertThat(properties.query().blockedKeywords()).containsExactly("CREATE", "MERGE");
                assertThat(properties.chunking().effectiveStrategy()).isEqualTo("fixed-character");
                assertThat(properties.chunking().effectiveTargetTokens()).isEqualTo(700);
                assertThat(properties.chunking().effectiveHardCharacterLimit()).isEqualTo(3500);
            });
    }

    @Test
    void failsWhenValidationIsViolated() {
        contextRunner
            .withPropertyValues(
                "app.neo4j.database=",
                "app.model.base-url=https://api.openai.com/v1",
                "app.model.embedding-model=text-embedding-3-small",
                "app.model.embedding-dimensions=0",
                "app.model.chat-model=gpt-5-mini",
                "app.storage.documents-root=./var/documents",
                "app.chunking.max-tokens=0",
                "app.chunking.overlap-tokens=80",
                "app.chunking.max-characters=4000",
                "app.query.max-rows=200",
                "app.query.timeout-seconds=15",
                "app.query.require-limit=true",
                "app.query.blocked-keywords=CREATE",
                "app.extraction.max-entities-per-chunk=40",
                "app.extraction.max-relationships-per-chunk=80",
                "app.extraction.max-retries=2"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("Could not bind properties");
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfiguration {
    }
}
