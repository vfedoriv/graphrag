package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.dto.RuntimeSettingResponse;
import io.github.vfedoriv.graphrag.repository.RuntimeSettingOverrideRepository;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeSettingsServiceTest {

    @Test
    void fallsBackToStartupDefaultsWhenNoOverridesExist() {
        RuntimeSettingsService service = service(new LinkedHashMap<>());

        assertThat(service.query().maxRows()).isEqualTo(200);
        assertThat(service.query().blockedKeywords()).containsExactly("CREATE", "DELETE");
        assertThat(service.chunking().maxCharacters()).isEqualTo(4000);
        assertThat(service.extraction().maxEntitiesPerChunk()).isEqualTo(40);
        assertThat(service.aiObservation().modelNameTagEnabled()).isTrue();
    }

    @Test
    void validatesPersistsAndAppliesLiveOverridesThroughTypedAccessors() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        RuntimeSettingResponse maxRows = service.update("app.query.max-rows", 25);
        service.update("app.query.blocked-keywords", List.of("CREATE", "MERGE"));
        service.update("app.chunking.max-characters", "1200");
        service.update("app.extraction.max-retries", 4);
        service.update("app.ai.observability.model-name-tag-enabled", false);

        assertThat(maxRows.source()).isEqualTo("override");
        assertThat(service.query().maxRows()).isEqualTo(25);
        assertThat(service.query().blockedKeywords()).containsExactly("CREATE", "MERGE");
        assertThat(service.chunking().maxCharacters()).isEqualTo(1200);
        assertThat(service.extraction().maxRetries()).isEqualTo(4);
        assertThat(service.aiObservation().modelNameTagEnabled()).isFalse();
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("25");
    }

    @Test
    void rejectsNonAllowlistedAndInvalidSettingUpdatesWithoutPersisting() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        assertThatThrownBy(() -> service.update("spring.neo4j.uri", "bolt://other"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not allowlisted");
        assertThatThrownBy(() -> service.update("app.query.max-rows", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("greater than or equal to 1");
        assertThatThrownBy(() -> service.update("app.query.require-limit", "sometimes"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("boolean");

        assertThat(store).isEmpty();
    }

    @Test
    void clearingOverrideFallsBackToStartupDefault() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        service.update("app.query.max-rows", 25);
        assertThat(service.query().maxRows()).isEqualTo(25);

        RuntimeSettingResponse cleared = service.clear("app.query.max-rows");

        assertThat(cleared.source()).isEqualTo("default");
        assertThat(service.query().maxRows()).isEqualTo(200);
        assertThat(store).doesNotContainKey("app.query.max-rows");
    }

    private RuntimeSettingsService service(Map<String, RuntimeSettingOverrideNode> store) {
        RuntimeSettingOverrideRepository repository = mock(RuntimeSettingOverrideRepository.class);
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(repository.save(any(RuntimeSettingOverrideNode.class))).thenAnswer(invocation -> {
            RuntimeSettingOverrideNode node = invocation.getArgument(0);
            store.put(node.getKey(), node);
            return node;
        });
        doAnswer(invocation -> {
            store.remove(invocation.getArgument(0));
            return null;
        }).when(repository).deleteById(anyString());
        return new RuntimeSettingsService(repository, appProperties(), observabilityProperties());
    }

    private AppProperties appProperties() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "secret", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE", "DELETE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private AiObservabilityProperties observabilityProperties() {
        return new AiObservabilityProperties(true, false, 512, true, 1024, true, true);
    }
}
