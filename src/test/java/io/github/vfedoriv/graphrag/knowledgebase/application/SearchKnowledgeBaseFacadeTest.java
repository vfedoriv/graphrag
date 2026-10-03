package io.github.vfedoriv.graphrag.knowledgebase.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.SearchKnowledgeBaseAccess;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchKnowledgeBaseFacadeTest {
    private final KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
    private final SearchKnowledgeBaseFacade facade = new SearchKnowledgeBaseFacade(repository);

    @Test
    void requireMapsRepositoryFactsWithoutStrengtheningManagedAdmission() throws Exception {
        KnowledgeBaseNode node = new KnowledgeBaseNode();
        node.setId("kb-1");
        node.setActiveSchemaId("schema-4");
        node.setActiveAiProfileId("profile-2");
        when(repository.findById("kb-1")).thenReturn(Optional.of(node));

        SearchKnowledgeBaseAccess.Facts facts = facade.require("kb-1");

        assertThat(facts).isEqualTo(new SearchKnowledgeBaseAccess.Facts("kb-1", "schema-4", "profile-2"));
        assertThat(facts.getClass().isRecord()).isTrue();
        assertThat(Arrays.stream(facts.getClass().getRecordComponents()).map(RecordComponent::getName))
            .containsExactly("knowledgeBaseId", "activeSchemaId", "activeAiProfileId");
        assertThat(new ObjectMapper().writeValueAsString(facts))
            .contains("kb-1", "schema-4", "profile-2")
            .doesNotContain("apiKey", "secret", "revision", "baseUrl");
        verify(repository).findById("kb-1");
    }

    @Test
    void requirePreservesKnowledgeBaseNotFoundSemantics() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.require("missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing");
    }

    @Test
    void existsUsesRepositoryExistenceWithoutLoadingManagedGraphState() {
        when(repository.existsById("kb-1")).thenReturn(true);
        when(repository.existsById("missing")).thenReturn(false);

        assertThat(facade.exists("kb-1")).isTrue();
        assertThat(facade.exists("missing")).isFalse();

        verify(repository).existsById("kb-1");
        verify(repository).existsById("missing");
        verify(repository, never()).findById("kb-1");
    }
}
