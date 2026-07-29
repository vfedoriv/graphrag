package io.github.vfedoriv.graphrag.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DocumentationAlignmentTest {

    private static final Pattern PARENT_VERSION = Pattern.compile(
        "<parent>\\s*<groupId>org\\.springframework\\.boot</groupId>\\s*<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>([^<]+)</version>",
        Pattern.DOTALL
    );
    private static final Pattern PROPERTY = Pattern.compile("<([^>]+)>\\s*([^<]+)\\s*</\\1>");

    @Test
    void sharedStackFactsMatchPom() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String readme = Files.readString(Path.of("README.md"));
        String agents = Files.readString(Path.of("AGENTS.md"));
        String claude = Files.readString(Path.of("CLAUDE.md"));

        Matcher bootMatcher = PARENT_VERSION.matcher(pom);
        assertThat(bootMatcher.find()).as("Spring Boot parent version in pom.xml").isTrue();
        String springBootVersion = bootMatcher.group(1);
        String springAiVersion = property(pom, "spring-ai.version");
        String langchainVersion = property(pom, "langchain4j.version");

        assertThat(readme).contains("Spring Boot " + springBootVersion, "Spring AI " + springAiVersion, "LangChain4j " + langchainVersion);
        assertThat(agents).contains("Spring Boot " + springBootVersion, "Spring AI " + springAiVersion, "LangChain4j " + langchainVersion);
        assertThat(claude).contains("Spring Boot " + springBootVersion, "Spring AI " + springAiVersion, "LangChain4j " + langchainVersion);
    }

    @Test
    void persistenceFreshStartGuidanceStaysAligned() throws IOException {
        String compose = Files.readString(Path.of("compose.yaml"));
        String runbook = Files.readString(Path.of("docs/PERSISTENCE_CUTOVER.md"));
        String readme = Files.readString(Path.of("README.md"));
        String agents = Files.readString(Path.of("AGENTS.md"));
        String claude = Files.readString(Path.of("CLAUDE.md"));

        assertThat(compose).contains("org.springframework.boot.ignore: true");
        assertThat(runbook)
            .contains(
                "docker compose --profile langfuse stop langfuse-web langfuse-worker neo4j",
                "--command=\"DROP SCHEMA IF EXISTS app CASCADE\"",
                "--command=\"DROP DATABASE IF EXISTS graphrag WITH (FORCE)\"",
                "docker volume rm graphrag_neo4j_data graphrag_neo4j_logs",
                "bash /docker-entrypoint-initdb.d/20-init-graphrag.sh",
                "SELECT current_database(), current_user, current_schema()",
                "curl --fail http://localhost:8080/actuator/health",
                "./mvnw test"
            )
            .doesNotContain("pg_dump", "pg_restore");

        assertThat(readme).contains("org.springframework.boot.ignore: true", "graphrag / graphrag / app", "one-time destructive fresh start");
        assertThat(agents).contains("ignored for Spring Boot service-connection discovery", "graphrag / graphrag / app", "one-time destructive fresh start");
        assertThat(claude).contains("ignored for Spring Boot service-connection discovery", "graphrag / graphrag / app", "one-time destructive fresh start");
    }

    private String property(String pom, String name) {
        Matcher matcher = PROPERTY.matcher(pom);
        while (matcher.find()) {
            if (matcher.group(1).equals(name)) {
                return matcher.group(2).trim();
            }
        }
        throw new AssertionError("Missing Maven property: " + name);
    }
}
