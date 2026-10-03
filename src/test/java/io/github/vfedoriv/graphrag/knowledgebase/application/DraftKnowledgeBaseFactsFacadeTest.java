package io.github.vfedoriv.graphrag.knowledgebase.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.DraftKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DraftKnowledgeBaseFactsFacadeTest {
    private final KnowledgeBaseLifecycleService lifecycle = mock(KnowledgeBaseLifecycleService.class);
    private final KnowledgeBaseService knowledgeBases = mock(KnowledgeBaseService.class);
    private final KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
    private final DraftKnowledgeBaseFactsFacade facade =
        new DraftKnowledgeBaseFactsFacade(lifecycle, knowledgeBases, repository);

    @Test
    void requireManagedDelegatesAndPropagatesMissingKnowledgeBase() {
        NotFoundException failure = new NotFoundException("Knowledge base not found: missing-kb");
        doThrow(failure).when(lifecycle).requireManaged("missing-kb");

        assertThatThrownBy(() -> facade.requireManaged("missing-kb"))
            .isSameAs(failure);
        verify(lifecycle).requireManaged("missing-kb");
    }

    @Test
    void activeSchemaIdReturnsEmptyWhenKnowledgeBaseIsMissing() {
        when(repository.findById("missing-kb")).thenReturn(Optional.empty());

        assertThat(facade.activeSchemaId("missing-kb")).isEmpty();
    }

    @Test
    void activeSchemaIdReturnsTheKnowledgeBaseSchemaId() {
        KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
        knowledgeBase.setActiveSchemaId("schema-7");
        when(repository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase));

        assertThat(facade.activeSchemaId("kb-1")).contains("schema-7");
    }

    @Test
    void activeProfileMapsOnlyNonSecretDraftProfileFacts() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-3");
        profile.setRevision(17L);
        profile.setTimeoutSeconds(23);
        profile.setMaxRetries(4);
        profile.setName("private profile name");
        profile.setBaseUrl("https://private.example");
        profile.setApiKey("private-api-key");
        profile.setChatModel("private-chat-model");
        profile.setEmbeddingModel("private-embedding-model");
        when(knowledgeBases.activeAiProfile("kb-1")).thenReturn(profile.facts());

        DraftKnowledgeBaseFacts.Profile facts = facade.activeProfile("kb-1");

        assertThat(facts).isEqualTo(new DraftKnowledgeBaseFacts.Profile("profile-3", 17L, 23, 4));
        assertThat(facts.toString())
            .doesNotContain("private profile name", "private.example", "private-api-key", "private-chat-model",
                "private-embedding-model");
        verify(knowledgeBases).activeAiProfile("kb-1");
    }
}
