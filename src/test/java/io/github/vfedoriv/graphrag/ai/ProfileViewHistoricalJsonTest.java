package io.github.vfedoriv.graphrag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;
import io.github.vfedoriv.graphrag.ai.models.EmptyObjectProvider;
import io.github.vfedoriv.graphrag.ai.ports.ProfileAssignments;
import io.github.vfedoriv.graphrag.ai.profiles.api.model.AiProfileResponse;
import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;
import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.ai.profiles.ports.AiProfileRepository;
import io.github.vfedoriv.graphrag.bootstrap.LegacyJacksonConfiguration;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileViewHistoricalJsonTest {
    @ParameterizedTest
    @ValueSource(strings = {"implicit", "explicit"})
    void publicViewPreservesHistoricalProfileJsonAndReadback(String mode) throws Exception {
        boolean explicit = "explicit".equals(mode);
        AiProfileNode stored = new AiProfileNode();
        stored.setId(mode + "-profile");
        stored.setName("Historical " + mode + " profile");
        stored.setBaseUrl("https://provider.example/v1");
        stored.setApiKey(explicit ? null : "sk-golden-secret-1234");
        stored.setChatModel("chat-model");
        stored.setEmbeddingModel(explicit ? "custom-model" : "text-embedding-3-small");
        stored.setTokenizerId(explicit ? new TokenizerId("cl100k_base") : null);
        stored.setEmbeddingDimensions(explicit ? 768 : 1536);
        stored.setTimeoutSeconds(60);
        stored.setMaxRetries(2);
        stored.setDefaultProfile(!explicit);
        stored.setRevision(7);
        stored.setCreatedAt(Instant.parse("2026-07-29T10:15:30Z"));
        stored.setUpdatedAt(Instant.parse("2026-07-30T11:16:31Z"));
        AiProfileRepository repository = mock(AiProfileRepository.class);
        when(repository.findById(stored.getId())).thenReturn(Optional.of(stored));
        AiProfileService profiles = new AiProfileService(repository,
            new ModelProperties("https://provider.example/v1", "", "text-embedding-3-small", 1536, "chat-model"),
            new EmptyObjectProvider<>(), new EmbeddingCompatibility(id -> List.of()), mock(ProfileAssignments.class));
        ObjectMapper mapper = new LegacyJacksonConfiguration().legacyObjectMapper();
        AiProfileResponse response = AiProfileResponse.from(profiles.inspect(stored.getId()));
        try (InputStream fixture = getClass().getResourceAsStream("/fixtures/ai/" + mode + "-tokenizer-profile-v1.json")) {
            assertThat(fixture).isNotNull();
            JsonNode historical = mapper.readTree(fixture);
            String serialized = mapper.writeValueAsString(response);
            com.fasterxml.jackson.databind.node.ObjectNode current = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(serialized);
            assertThat(current.remove("structuredOutputMode").asText()).isEqualTo("PORTABLE");
            assertThat(current).isEqualTo(historical);
            assertThat(mapper.treeToValue(historical, AiProfileResponse.class)).isEqualTo(response);
            assertThat(serialized).doesNotContain("sk-golden-secret-1234", "\"apiKey\":");
        }
    }
}
