package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.dto.AiProfileResponse;
import io.github.vfedoriv.graphrag.dto.CreateAiProfileRequest;
import io.github.vfedoriv.graphrag.dto.UpdateAiProfileRequest;
import io.github.vfedoriv.graphrag.document.chunking.TokenizerId;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.EmbeddingSpaceConflictException;
import io.github.vfedoriv.graphrag.repository.AiProfileRepository;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiProfileServiceTest {

    @Test
    void seedsDefaultProfileFromStartupPropertiesOnlyWhenMissing() {
        Map<String, AiProfileNode> store = new LinkedHashMap<>();
        AiProfileService service = service(store);

        AiProfileNode seeded = service.seedDefaultProfile();

        assertThat(seeded.getId()).isEqualTo(AiProfileService.DEFAULT_PROFILE_ID);
        assertThat(seeded.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(seeded.getApiKey()).isEqualTo("startup-key");
        assertThat(seeded.getChatModel()).isEqualTo("gpt-5-mini");
        assertThat(seeded.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(seeded.getEmbeddingDimensions()).isEqualTo(1536);

        seeded.setChatModel("persisted-chat");
        assertThat(service.seedDefaultProfile().getChatModel()).isEqualTo("persisted-chat");
    }

    @Test
    void createReturnsMaskedSecretMetadataWithoutRawApiKey() {
        Map<String, AiProfileNode> store = new LinkedHashMap<>();
        AiProfileService service = service(store);

        AiProfileResponse response = service.create(new CreateAiProfileRequest(
            "profile-1",
            "Profile 1",
            "https://profiles.example/v1",
            "sk-1234567890abcdef",
            "chat-model",
            "embedding-model",
            768,
            30,
            1,
            false
        ));

        assertThat(response.id()).isEqualTo("profile-1");
        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(response.apiKeyMask()).isEqualTo("sk-1...cdef");
        assertThat(response.toString()).doesNotContain("sk-1234567890abcdef");
        assertThat(store.get("profile-1").getApiKey()).isEqualTo("sk-1234567890abcdef");
    }

    @Test
    void updateOmittingApiKeyRetainsExistingSecretAndClearRemovesIt() {
        Map<String, AiProfileNode> store = new LinkedHashMap<>();
        AiProfileService service = service(store);
        service.create(new CreateAiProfileRequest(
            "profile-1",
            "Profile 1",
            "https://profiles.example/v1",
            "secret-key",
            "chat-model",
            "embedding-model",
            768,
            null,
            null,
            false
        ));

        AiProfileResponse retained = service.update("profile-1", new UpdateAiProfileRequest(
            "Profile 1 renamed",
            "https://profiles.example/v2",
            null,
            false,
            "chat-model-2",
            "embedding-model",
            768,
            45,
            3,
            false
        ));

        assertThat(store.get("profile-1").getApiKey()).isEqualTo("secret-key");
        assertThat(retained.apiKeyConfigured()).isTrue();
        assertThat(retained.revision()).isEqualTo(2);

        AiProfileResponse cleared = service.update("profile-1", new UpdateAiProfileRequest(
            "Profile 1 renamed",
            "https://profiles.example/v2",
            null,
            true,
            "chat-model-2",
            "embedding-model",
            768,
            45,
            3,
            false
        ));

        assertThat(store.get("profile-1").getApiKey()).isNull();
        assertThat(cleared.apiKeyConfigured()).isFalse();
        assertThat(cleared.apiKeyMask()).isNull();
    }

    @Test
    void invalidProfileDataIsRejectedWithoutPersisting() {
        Map<String, AiProfileNode> store = new LinkedHashMap<>();
        AiProfileService service = service(store);

        assertThatThrownBy(() -> service.create(new CreateAiProfileRequest(
            "bad",
            "Bad",
            "not-a-url",
            null,
            "chat",
            "embed",
            768,
            null,
            null,
            false
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("valid absolute URL");

        assertThat(store).doesNotContainKey("bad");
    }

    @Test
    void explicitTokenizerIsValidatedResolvedAndIncludedInRevisionedResponse() {
        Map<String, AiProfileNode> store = new LinkedHashMap<>();
        AiProfileService service = service(store);

        AiProfileResponse created = service.create(new CreateAiProfileRequest(
            "profile-tokenizer",
            "Profile tokenizer",
            "https://profiles.example/v1",
            null,
            "chat",
            "embedding-alias",
            TokenizerId.CL100K_BASE,
            768,
            null,
            null,
            false
        ));

        assertThat(created.tokenizerId()).isEqualTo(TokenizerId.CL100K_BASE);
        assertThat(created.resolvedTokenizerId()).isEqualTo(TokenizerId.CL100K_BASE);
        assertThat(created.revision()).isEqualTo(1);
        assertThat(created.toString()).doesNotContain("apiKey=");

        assertThatThrownBy(() -> service.update("profile-tokenizer", new UpdateAiProfileRequest(
            "Profile tokenizer",
            "https://profiles.example/v1",
            null,
            false,
            "chat",
            "embedding-alias",
            "unsupported-tokenizer",
            768,
            null,
            null,
            false
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported tokenizerId");

        assertThat(store.get("profile-tokenizer").getTokenizerId().value()).isEqualTo(TokenizerId.CL100K_BASE);
        assertThat(store.get("profile-tokenizer").getRevision()).isEqualTo(1);
    }

    @Test
    void deleteRejectsDefaultAndAssignedProfiles() {
        Map<String, AiProfileNode> store = new LinkedHashMap<>();
        AiProfileRepository repository = repository(store);
        when(repository.existsKnowledgeBaseAssignment("profile-1")).thenReturn(true);
        AiProfileService service = new AiProfileService(repository, appProperties(), new EmptyObjectProvider<>());
        service.seedDefaultProfile();
        service.create(new CreateAiProfileRequest(
            "profile-1",
            "Profile 1",
            "https://profiles.example/v1",
            null,
            "chat",
            "embed",
            768,
            null,
            null,
            false
        ));

        assertThatThrownBy(() -> service.delete(AiProfileService.DEFAULT_PROFILE_ID))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Default AI profile");
        assertThatThrownBy(() -> service.delete("profile-1"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("assigned");
        verify(repository, never()).deleteById(anyString());
    }

    @Test
    void rejectsSharedProfileUpdateAtomicallyWhenAnAssignedKnowledgeBaseHasAnotherEmbeddingSpace() {
        Map<String, AiProfileNode> store = new LinkedHashMap<>();
        AiProfileRepository profileRepository = repository(store);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        AiProfileService service = new AiProfileService(
            profileRepository,
            appProperties(),
            new EmptyObjectProvider<>(),
            new EmbeddingSpacePolicy(chunkRepository)
        );
        service.create(new CreateAiProfileRequest(
            "shared",
            "Shared",
            "https://api.openai.com/v1",
            null,
            "chat",
            "embed",
            768,
            null,
            null,
            false
        ));
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setId("chunk-1");
        chunk.setEmbeddingSpaceId(EmbeddingSpaceIdentity.derive("https://api.openai.com/v1", "embed", 768).id());
        when(profileRepository.findAssignedKnowledgeBaseIds("shared")).thenReturn(List.of("kb-1", "kb-2"));
        when(chunkRepository.findEmbeddedChunksByKnowledgeBaseId("kb-1")).thenReturn(List.of(chunk));
        when(chunkRepository.findEmbeddedChunksByKnowledgeBaseId("kb-2")).thenReturn(List.of(chunk));

        assertThatThrownBy(() -> service.update("shared", new UpdateAiProfileRequest(
            "Shared",
            "https://other-provider.example/v1",
            null,
            false,
            "chat",
            "embed",
            768,
            null,
            null,
            false
        ))).isInstanceOf(EmbeddingSpaceConflictException.class)
            .hasMessageContaining("assigned knowledge bases");

        assertThat(store.get("shared").getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(store.get("shared").getRevision()).isEqualTo(1);
    }

    private AiProfileService service(Map<String, AiProfileNode> store) {
        return new AiProfileService(repository(store), appProperties(), new EmptyObjectProvider<>());
    }

    private AiProfileRepository repository(Map<String, AiProfileNode> store) {
        AiProfileRepository repository = mock(AiProfileRepository.class);
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(repository.existsById(anyString())).thenAnswer(invocation -> store.containsKey(invocation.getArgument(0)));
        when(repository.findAllByOrderByCreatedAtDesc()).thenAnswer(invocation -> List.copyOf(store.values()));
        when(repository.findFirstByDefaultProfileTrue()).thenAnswer(invocation -> store.values().stream()
            .filter(AiProfileNode::isDefaultProfile)
            .findFirst());
        when(repository.save(any(AiProfileNode.class))).thenAnswer(invocation -> {
            AiProfileNode profile = invocation.getArgument(0);
            store.put(profile.getId(), profile);
            return profile;
        });
        doAnswer(invocation -> {
            String profileId = invocation.getArgument(0);
            store.values().stream()
                .filter(profile -> !profile.getId().equals(profileId))
                .forEach(profile -> profile.setDefaultProfile(false));
            return null;
        }).when(repository).unsetDefaultProfileForOthers(anyString());
        doAnswer(invocation -> {
            store.remove(invocation.getArgument(0));
            return null;
        }).when(repository).deleteById(anyString());
        return repository;
    }

    private AppProperties appProperties() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "startup-key", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
