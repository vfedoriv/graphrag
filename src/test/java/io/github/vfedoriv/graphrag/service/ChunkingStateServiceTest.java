package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.ChunkingStateDtos.ChunkingStateResponse;
import io.github.vfedoriv.graphrag.dto.RuntimeSettingResponse;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChunkingStateServiceTest {

    @Test
    void reportsCompatibilityAliasPrecedenceAndTypedEffectiveValue() {
        RuntimeSettingsService runtime = mock(RuntimeSettingsService.class);
        when(runtime.chunking()).thenReturn(new RuntimeSettingsService.ChunkingSettings(
            "recursive", 900, 80, 4000, 1800, 8000, 2, 64, 256, "representation-v1"
        ));
        when(runtime.effectiveChunkerRevision()).thenReturn("chunker_" + "a".repeat(64));
        when(runtime.list()).thenReturn(List.of(
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
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://example.test", "", "unknown-model", 3, "chat"),
            new AppProperties.Storage(Path.of("/tmp/documents")),
            new AppProperties.Chunking("fixed-character", 800, 80, 4000, 800, 4000, 64, 256, "representation-v1"),
            new AppProperties.Query(100, 30, false, List.of("delete")),
            new AppProperties.Extraction(10, 10, 1)
        );

        ChunkingStateResponse state = new ChunkingStateService(runtime, appProperties).get();

        assertThat(state.targetTokens()).isEqualTo(900);
        assertThat(state.valueSources()).containsEntry("app.chunking.target-tokens", "compatibility-alias");
        assertThat(state.compatibilityAliases()).first()
            .satisfies(alias -> {
                assertThat(alias.authoritative()).isFalse();
                assertThat(alias.effectiveValue()).isEqualTo(900);
                assertThat(alias.precedence()).isEqualTo("compatibility-alias");
            });
    }

    private RuntimeSettingResponse setting(String key, Object value, Object defaultValue, String source) {
        return new RuntimeSettingResponse(
            key, "chunking", "integer", value, defaultValue, value, source, "active", true, true, false,
            Map.of(), "live", null, key, key
        );
    }
}
