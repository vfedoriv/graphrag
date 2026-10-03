package io.github.vfedoriv.graphrag.ai;

import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;
import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileFactsTest {
    @Test
    void immutableFactsRetainCapturedRevisionAndNeverContainCredentials() {
        AiProfileNode state = new AiProfileNode();
        state.setId("profile");
        state.setRevision(2);
        state.setBaseUrl("https://provider.example/v1");
        state.setApiKey("secret");
        state.setEmbeddingModel("embedding");
        state.setTokenizerId(new TokenizerId("cl100k_base"));
        state.setEmbeddingDimensions(768);
        ProfileFacts captured = state.facts();
        state.setRevision(3);
        state.setEmbeddingModel("replacement");
        state.setApiKey("replacement-secret");
        assertThat(captured.revision()).isEqualTo(2);
        assertThat(captured.embeddingModel()).isEqualTo("embedding");
        assertThat(captured.tokenizerId().value()).isEqualTo("cl100k_base");
        assertThat(Arrays.stream(ProfileFacts.class.getRecordComponents()).map(component -> component.getName()))
            .doesNotContain("apiKey", "credentials", "client");
        assertThat(captured.toString()).doesNotContain("secret");
    }
}
