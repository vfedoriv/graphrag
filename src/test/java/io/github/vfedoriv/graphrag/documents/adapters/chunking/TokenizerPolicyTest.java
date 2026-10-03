package io.github.vfedoriv.graphrag.documents.adapters.chunking;

import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenCountMode;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenEstimator;
import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TokenizerPolicyTest {

    private final TokenizerPolicy policy = new TokenizerPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
        "text-embedding-ada-002",
        "text-embedding-3-small",
        "text-embedding-3-large"
    })
    void resolvesKnownEmbeddingModelsToCl100k(String model) {
        TokenEstimator estimator = policy.resolve(null, model);

        assertThat(estimator.tokenizerId().value()).isEqualTo(TokenizerId.CL100K_BASE);
        assertThat(estimator.countMode()).isEqualTo(TokenCountMode.EXACT);
        assertThat(estimator.count("hello world")).isEqualTo(2);
    }

    @Test
    void acceptsSupportedExplicitTokenizerForModelAlias() {
        TokenEstimator estimator = policy.resolve(new TokenizerId(TokenizerId.CL100K_BASE), "deployment-alias");

        assertThat(estimator.tokenizerId().value()).isEqualTo(TokenizerId.CL100K_BASE);
        assertThat(estimator.countMode()).isEqualTo(TokenCountMode.EXACT);
    }

    @Test
    void rejectsUnknownExplicitTokenizer() {
        assertThatThrownBy(() -> policy.validateExplicit("unknown-tokenizer"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported tokenizerId");
    }

    @Test
    void unknownModelUsesDeterministicUtf8ByteFallback() {
        TokenEstimator estimator = policy.resolve(null, "local-embedding-model");
        String text = "Привіт 🌍";

        assertThat(estimator.tokenizerId().value()).isEqualTo(TokenizerId.UTF8_BYTE_V1);
        assertThat(estimator.countMode()).isEqualTo(TokenCountMode.CONSERVATIVE);
        assertThat(estimator.count(text)).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
    }
}
