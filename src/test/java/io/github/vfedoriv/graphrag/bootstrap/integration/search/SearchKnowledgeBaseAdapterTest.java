package io.github.vfedoriv.graphrag.bootstrap.integration.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.SearchKnowledgeBaseAccess;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchKnowledgeBases;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SearchKnowledgeBaseAdapterTest {
    private final SearchKnowledgeBaseAccess source = mock(SearchKnowledgeBaseAccess.class);
    private final SearchKnowledgeBaseAdapter adapter = new SearchKnowledgeBaseAdapter(source);

    @Test
    void mapsOnlyImmutableNonSecretFacts() throws Exception {
        when(source.require("kb-1"))
            .thenReturn(new SearchKnowledgeBaseAccess.Facts("kb-1", "schema-4", "profile-2"));

        SearchKnowledgeBases.Facts facts = adapter.require("kb-1");

        assertThat(facts).isEqualTo(new SearchKnowledgeBases.Facts("kb-1", "schema-4", "profile-2"));
        assertThat(facts.getClass().isRecord()).isTrue();
        assertThat(Arrays.stream(facts.getClass().getRecordComponents()).map(RecordComponent::getName))
            .containsExactly("knowledgeBaseId", "activeSchemaId", "activeAiProfileId");
        assertThat(new ObjectMapper().writeValueAsString(facts))
            .doesNotContain("apiKey", "secret", "revision", "baseUrl");
        verify(source).require("kb-1");
    }

    @Test
    void delegatesExistenceAsAnExistenceCheck() {
        when(source.exists("kb-1")).thenReturn(true);

        assertThat(adapter.exists("kb-1")).isTrue();

        verify(source).exists("kb-1");
    }

    @Test
    void adapterAddsNoRelationalTransactionBoundary() {
        assertThat(SearchKnowledgeBaseAdapter.class.isAnnotationPresent(RelationalTransactional.class)).isFalse();
        assertThat(Arrays.stream(SearchKnowledgeBaseAdapter.class.getDeclaredMethods())
            .anyMatch(method -> method.isAnnotationPresent(RelationalTransactional.class)))
            .isFalse();
        assertThat(Arrays.stream(SearchKnowledgeBaseAdapter.class.getDeclaredFields())
            .map(field -> field.getType().getPackageName()))
            .containsExactly("io.github.vfedoriv.graphrag.knowledgebase.contracts");
    }
}
