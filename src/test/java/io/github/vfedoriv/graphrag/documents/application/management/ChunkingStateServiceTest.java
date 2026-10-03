package io.github.vfedoriv.graphrag.documents.application.management;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;

import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
import io.github.vfedoriv.graphrag.documents.api.model.ChunkingStateDtos.ChunkingStateResponse;
import io.github.vfedoriv.graphrag.settings.api.model.RuntimeSettingResponse;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChunkingStateServiceTest {

    @Test
    void reportsCompatibilityAliasPrecedenceAndTypedEffectiveValue() {
        RuntimeSettingsAccess runtime = mock(RuntimeSettingsAccess.class);
        when(runtime.chunking()).thenReturn(new RuntimeSettingsAccess.ChunkingSettings(
            "recursive", 900, 80, 4000, 1800, 8000, 2, 64, 256, "representation-v1"
        ));
        when(runtime.effectiveChunkerRevision()).thenReturn("chunker_" + "a".repeat(64));
        when(runtime.chunkingFacts()).thenReturn(List.of(
            setting("app.chunking.target-tokens", 800, 800, "default"),
            setting("app.chunking.max-tokens", 900, 800, "override"),
            setting("app.chunking.hard-character-limit", 4000, 4000, "default"),
            setting("app.chunking.max-characters", 4000, 4000, "default"),
            setting("app.chunking.overlap-tokens", 80, 80, "default"),
            setting("app.chunking.parent-target-tokens", 1800, 1800, "default"),
            setting("app.chunking.parent-hard-character-limit", 8000, 8000, "default"),
            setting("app.chunking.parent-max-pages", 2, 2, "default"),
            setting("app.chunking.context-header-max-tokens", 64, 64, "default"),
            setting("app.chunking.context-header-max-characters", 256, 256, "default"),
            setting("app.chunking.strategy", "recursive", "recursive", "default"),
            setting("app.chunking.representation-revision", "representation-v1", "representation-v1", "default")
        ));
        AppProperties appProperties = new AppProperties(
            new Neo4jProperties("neo4j"),
            new ModelProperties("https://example.test", "", "unknown-model", 3, "chat"),
            new StorageProperties(Path.of("/tmp/documents")),
            new ChunkingProperties("fixed-character", 800, 80, 4000, 800, 4000, 64, 256, "representation-v1"),
            new QueryProperties(100, 30, false, List.of("delete")),
            new ExtractionProperties(10, 10, 1)
        );

        ChunkingStateResponse state = new ChunkingStateService(runtime,io.github.vfedoriv.graphrag.TestRuntimeSettings.modelMetadata(appProperties)).get();

        assertThat(state.targetTokens()).isEqualTo(900);
        // The reported revision must use this snapshot, even if a later settings read differs.
        assertThat(state.effectiveChunkerRevision()).isEqualTo("chunker_9f4f64902eef4d85a9c762905f3ef05d334e53fda7b932b39cd86404711390a8");
        assertThat(state.valueSources()).containsEntry("app.chunking.target-tokens", "compatibility-alias");
        assertThat(state.compatibilityAliases()).first()
            .satisfies(alias -> {
                assertThat(alias.authoritative()).isFalse();
                assertThat(alias.effectiveValue()).isEqualTo(900);
                assertThat(alias.precedence()).isEqualTo("compatibility-alias");
            });
    }

    private RuntimeSettingsAccess.ChunkingSettingFact setting(String key, Object value, Object defaultValue, String source) {
        return new RuntimeSettingsAccess.ChunkingSettingFact(key, source, value, "explicit-reprocessing-required");
    }
}
