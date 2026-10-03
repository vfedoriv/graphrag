package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;

import io.github.vfedoriv.graphrag.ai.models.EmbeddingClient;

import io.github.vfedoriv.graphrag.ai.adapters.provider.AiRuntimeModelFactory;

import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.StoredEmbeddingObservation;
import io.github.vfedoriv.graphrag.ai.ports.StoredEmbeddingInformation;
import io.github.vfedoriv.graphrag.bootstrap.integration.search.SearchKnowledgeBaseAdapter;
import io.github.vfedoriv.graphrag.bootstrap.integration.search.SearchSchemaAdapter;
import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.knowledgebase.application.SearchKnowledgeBaseFacade;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.schemas.contracts.CapturedSchemaParsing;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.search.runs.adapters.model.SearchProfileAdapter;
import io.github.vfedoriv.graphrag.search.runs.application.AdvancedSearchReadinessService;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchKnowledgeBases;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

class AdvancedSearchReadinessServiceTest {
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final AiProfileService aiProfileService = mock(AiProfileService.class);
    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final AiRuntimeModelFactory runtimeModelFactory = mock(AiRuntimeModelFactory.class);
    private final StoredSchemaSnapshots storedSchemas = mock(StoredSchemaSnapshots.class);
    private final SchemaSnapshots activeSchemas = mock(SchemaSnapshots.class);
    private final CapturedSchemaParsing capturedSchemaParsing = mock(CapturedSchemaParsing.class);
    private final StoredEmbeddingInformation storedEmbeddings = mock(StoredEmbeddingInformation.class);
    private final SearchKnowledgeBases searchKnowledgeBases = new SearchKnowledgeBaseAdapter(
        new SearchKnowledgeBaseFacade(knowledgeBaseRepository)
    );
    private final SearchProfiles searchProfiles = new SearchProfileAdapter(
        aiProfileService, knowledgeBaseService, runtimeModelFactory
    );
    private final SearchSchemas searchSchemas = new SearchSchemaAdapter(
        storedSchemas, activeSchemas, capturedSchemaParsing
    );
    private final EmbeddingCompatibility embeddingCompatibility = new EmbeddingCompatibility(storedEmbeddings);
    private final AdvancedSearchReadinessService service = new AdvancedSearchReadinessService(
        searchKnowledgeBases, searchProfiles, searchSchemas, embeddingCompatibility, searchProfiles
    );
    private final KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
    private final AiProfileNode profile = new AiProfileNode();
    private final ChatModel chatModel = mock(ChatModel.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    @BeforeEach
    void setUp() {
        knowledgeBase.setId("kb-1");
        knowledgeBase.setActiveSchemaId("schema-1");
        knowledgeBase.setActiveAiProfileId("profile-1");
        profile.setId("profile-1");
        profile.setRevision(4);
        profile.setBaseUrl("https://provider.example/v1");
        profile.setChatModel("chat-model");
        profile.setEmbeddingModel("embedding-model");
        profile.setEmbeddingDimensions(3);
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(aiProfileService.require("profile-1")).thenReturn(profile.facts());
        when(storedSchemas.findById("schema-1")).thenReturn(Optional.empty());
        when(storedEmbeddings.observations("kb-1")).thenReturn(List.of());
        when(runtimeModelFactory.chatModel("profile-1")).thenReturn(chatModel);
        when(runtimeModelFactory.embeddingModel("profile-1")).thenReturn(embeddingModel);
    }

    @Test
    void reportsEmptyCorpusAndMissingSchemaAsInformationalWhileReady() {
        io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchReadinessDtos.ReadinessResponse result =
            service.evaluate("kb-1");

        assertThat(result.ready()).isTrue();
        assertThat(result.profileId()).isEqualTo("profile-1");
        assertThat(result.profileRevision()).isEqualTo(4);
        assertThat(result.graphBranchAvailable()).isFalse();
        assertThat(result.embeddedCorpusPresent()).isFalse();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.informational()).extracting(issue -> issue.code())
            .containsExactly("SCHEMA_UNAVAILABLE", "EMPTY_CORPUS");
        verifyNoInteractions(activeSchemas, capturedSchemaParsing, embeddingModel);
    }

    @Test
    void blocksIncompatibleEmbeddingsWithStableCodeWithoutProviderRequests() {
        when(storedEmbeddings.observations("kb-1"))
            .thenReturn(List.of(new StoredEmbeddingObservation("es_legacy", "old-model", "legacy-tokenizer")));

        io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchReadinessDtos.ReadinessResponse result =
            service.evaluate("kb-1");

        assertThat(result.ready()).isFalse();
        assertThat(result.blockers()).extracting(issue -> issue.code())
            .containsExactly("EMBEDDING_SPACE_INCOMPATIBLE");
        verifyNoInteractions(chatModel, embeddingModel, activeSchemas, capturedSchemaParsing);
    }

    @Test
    void doesNotConstructAnEmbeddingClientForAnEmptyCorpus() {
        service.evaluate("kb-1");

        verify(runtimeModelFactory, never()).embeddingModel("profile-1");
    }
}
