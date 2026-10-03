package io.github.vfedoriv.graphrag.storage;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;
import io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
import io.github.vfedoriv.graphrag.settings.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.settings.ports.RuntimeSettingOverrideRepository;
import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class LocalFilesystemBinaryStorageServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    @TempDir
    java.nio.file.Path overrideDir;

    @Test
    void generatesFileUriAndReadsBackBytes() throws Exception {
        AppProperties props = appProperties(tempDir);
        LocalFilesystemBinaryStorageService storage = new LocalFilesystemBinaryStorageService(TestRuntimeSettings.from(props));

        URI uri = storage.store("kb-1", "doc-1", "my doc.txt", "abc".getBytes());
        assertThat(uri.toString()).startsWith("file:");
        assertThat(storage.resolvePath(uri)).exists();
        assertThat(Files.readString(storage.resolvePath(uri))).isEqualTo("abc");

        try (InputStream stream = storage.read(uri)) {
            assertThat(new String(stream.readAllBytes())).isEqualTo("abc");
        }

        storage.delete(uri);
        assertThat(storage.resolvePath(uri)).doesNotExist();
    }

    @Test
    void usesStartupLoadedRuntimeStorageRootOverride() throws Exception {
        AppProperties props = appProperties(tempDir);
        RuntimeSettingOverrideNode override = new RuntimeSettingOverrideNode();
        override.setKey("app.storage.documents-root");
        override.setValue(overrideDir.toString());
        RuntimeSettingOverrideRepository repository = mock(RuntimeSettingOverrideRepository.class);
        when(repository.findById(anyString())).thenAnswer(invocation -> {
            if ("app.storage.documents-root".equals(invocation.getArgument(0))) {
                return Optional.of(override);
            }
            return Optional.empty();
        });
        RuntimeSettingsService settings = new RuntimeSettingsService(
            repository,io.github.vfedoriv.graphrag.TestRuntimeSettings.startupDefaults(props),
            io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties.disabled(),
            new MockEnvironment()
        , new io.github.vfedoriv.graphrag.bootstrap.integration.settings.SettingsChunkRevisionAdapter(new io.github.vfedoriv.graphrag.documents.application.inspection.DocumentChunkRevisionsFacade()));
        LocalFilesystemBinaryStorageService storage = new LocalFilesystemBinaryStorageService(settings);

        URI uri = storage.store("kb-1", "doc-1", "my doc.txt", "abc".getBytes());

        assertThat(storage.resolvePath(uri)).startsWith(overrideDir.toAbsolutePath().normalize());
    }

    private AppProperties appProperties(java.nio.file.Path documentsRoot) {
        return new AppProperties(
            new Neo4jProperties("neo4j"),
            new ModelProperties("http://localhost", "k", "m1", 10, "m2"),
            new StorageProperties(documentsRoot),
            new ChunkingProperties(1, 0, 1),
            new QueryProperties(1, 1, true, java.util.List.of("CREATE")),
            new ExtractionProperties(1, 1, 0)
        );
    }
}
