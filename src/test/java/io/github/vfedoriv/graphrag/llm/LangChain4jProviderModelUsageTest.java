package io.github.vfedoriv.graphrag.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LangChain4jProviderModelUsageTest {

    @Test
    void productionCodeDoesNotImportLangChain4jProviderModels() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> violations = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> containsProviderModelImport(path))
                .map(sourceRoot::relativize)
                .toList();

            assertThat(violations)
                .as("Spring AI owns provider model runtime. LangChain4j provider model imports must not be used.")
                .isEmpty();
        }
    }

    private boolean containsProviderModelImport(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("import dev.langchain4j.model.openai.")
                || content.contains("import dev.langchain4j.model.ollama.")
                || content.contains("import dev.langchain4j.model.azure.")
                || content.contains("import dev.langchain4j.model.anthropic.")
                || content.contains("import dev.langchain4j.model.googleai.")
                || content.contains("import dev.langchain4j.model.vertexai.");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect source file " + path, e);
        }
    }
}
