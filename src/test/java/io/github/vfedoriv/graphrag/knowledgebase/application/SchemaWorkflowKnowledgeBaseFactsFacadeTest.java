package io.github.vfedoriv.graphrag.knowledgebase.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.SchemaWorkflowKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchemaWorkflowKnowledgeBaseFactsFacadeTest {
    @Test void returnsDetachedNonSecretProfileAndCurrentTargetFacts() throws Exception {
        KnowledgeBaseLifecycleService lifecycle = mock(KnowledgeBaseLifecycleService.class);
        KnowledgeBaseService service = mock(KnowledgeBaseService.class);
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        SchemaWorkflowKnowledgeBaseFactsFacade facade = new SchemaWorkflowKnowledgeBaseFactsFacade(lifecycle, service, repository);
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile"); profile.setRevision(9); profile.setApiKey("secret-must-not-leak");
        profile.setBaseUrl("https://provider/v1"); profile.setEmbeddingModel("embedding"); profile.setEmbeddingDimensions(1536);
        when(service.activeAiProfile("kb")).thenReturn(profile);
        SchemaWorkflowKnowledgeBaseFacts.Profile facts = facade.activeProfile("kb");
        profile.setRevision(10); profile.setEmbeddingModel("changed");
        assertThat(facts.id()).isEqualTo("profile"); assertThat(facts.revision()).isEqualTo(9);
        assertThat(facts.baseUrl()).isEqualTo("https://provider/v1"); assertThat(facts.embeddingModel()).isEqualTo("embedding");
        assertThat(facts.embeddingDimensions()).isEqualTo(1536);
        assertThat(new ObjectMapper().writeValueAsString(facts)).doesNotContain("apiKey", "secret-must-not-leak");
        KnowledgeBaseNode kb = new KnowledgeBaseNode(); kb.setId("kb"); kb.setActiveSchemaId("schema");
        when(repository.findById("kb")).thenReturn(Optional.of(kb));
        when(lifecycle.requireManaged("kb")).thenReturn(kb);
        SchemaWorkflowKnowledgeBaseFacts.KnowledgeBase target = facade.requireManaged("kb");
        kb.setActiveSchemaId("changed");
        assertThat(target.activeSchemaId()).isEqualTo("schema");
        assertThat(facade.find("kb").orElseThrow().activeSchemaId()).isEqualTo("changed");
        assertThat(facade.find("missing")).isEmpty();
    }
}
