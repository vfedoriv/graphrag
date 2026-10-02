package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

class AdvancedSearchReadinessServiceTest {
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final AiProfileService aiProfileService = mock(AiProfileService.class);
    private final SchemaDefinitionRepository schemaDefinitionRepository = mock(SchemaDefinitionRepository.class);
    private final EmbeddingSpacePolicy embeddingSpacePolicy = mock(EmbeddingSpacePolicy.class);
    private final AiRuntimeModelFactory runtimeModelFactory = mock(AiRuntimeModelFactory.class);
    private final AdvancedSearchReadinessService service = new AdvancedSearchReadinessService(
        knowledgeBaseRepository, aiProfileService, schemaDefinitionRepository, embeddingSpacePolicy, runtimeModelFactory);
    private final KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
    private final AiProfileNode profile = new AiProfileNode();

    @BeforeEach
    void setUp() {
        knowledgeBase.setId("kb-1");
        knowledgeBase.setActiveAiProfileId("profile-1");
        profile.setId("profile-1");
        profile.setRevision(4);
        profile.setBaseUrl("https://provider.example/v1");
        profile.setChatModel("chat-model");
        profile.setEmbeddingModel("embedding-model");
        profile.setEmbeddingDimensions(3);
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(java.util.Optional.of(knowledgeBase));
        when(aiProfileService.getNode("profile-1")).thenReturn(profile);
        when(schemaDefinitionRepository.findById("schema-1")).thenReturn(java.util.Optional.empty());
        when(embeddingSpacePolicy.hasEmbeddedChunks("kb-1")).thenReturn(false);
        when(runtimeModelFactory.chatModel("profile-1")).thenReturn(mock(ChatModel.class));
        when(runtimeModelFactory.embeddingModel("profile-1")).thenReturn(mock(EmbeddingModel.class));
    }

    @Test
    void reportsEmptyCorpusAndMissingSchemaAsInformationalWhileReady() {
        io.github.vfedoriv.graphrag.dto.AdvancedSearchReadinessDtos.ReadinessResponse result = service.evaluate("kb-1");

        assertThat(result.ready()).isTrue();
        assertThat(result.profileId()).isEqualTo("profile-1");
        assertThat(result.profileRevision()).isEqualTo(4);
        assertThat(result.graphBranchAvailable()).isFalse();
        assertThat(result.embeddedCorpusPresent()).isFalse();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.informational()).extracting(issue -> issue.code())
            .containsExactly("SCHEMA_UNAVAILABLE", "EMPTY_CORPUS");
    }

    @Test
    void blocksIncompatibleEmbeddingsWithStableCode() {
        when(embeddingSpacePolicy.hasEmbeddedChunks("kb-1")).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("incompatible"))
            .when(embeddingSpacePolicy).requireCompatible("kb-1", profile);

        io.github.vfedoriv.graphrag.dto.AdvancedSearchReadinessDtos.ReadinessResponse result = service.evaluate("kb-1");

        assertThat(result.ready()).isFalse();
        assertThat(result.blockers()).extracting(issue -> issue.code())
            .containsExactly("EMBEDDING_SPACE_INCOMPATIBLE");
    }

    @Test
    void doesNotCallEmbeddingFactoryForAnEmptyCorpus() {
        service.evaluate("kb-1");

        org.mockito.Mockito.verify(runtimeModelFactory, org.mockito.Mockito.never()).embeddingModel("profile-1");
    }
}
