package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.ai.domain.EmbeddingSpaceIdentity;

import io.github.vfedoriv.graphrag.ai.domain.EmbeddingSpace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddingSpaceIdentityTest {

    @Test
    void normalizesEquivalentEndpointsWithoutIncludingCredentials() {
        EmbeddingSpace first = EmbeddingSpaceIdentity.derive(
            "HTTPS://API.EXAMPLE.COM:443/v1/", "embed-model", 1536
        );
        EmbeddingSpace second = EmbeddingSpaceIdentity.derive(
            "https://api.example.com/v1", "embed-model", 1536
        );

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.id()).doesNotContain("key").startsWith("es_");
    }

    @Test
    void differentiatesProvidersAndDimensions() {
        EmbeddingSpace providerA = EmbeddingSpaceIdentity.derive("https://provider-a.example/v1", "embed", 768);
        EmbeddingSpace providerB = EmbeddingSpaceIdentity.derive("https://provider-b.example/v1", "embed", 768);
        EmbeddingSpace otherDimensions = EmbeddingSpaceIdentity.derive("https://provider-a.example/v1", "embed", 1536);

        assertThat(providerA.id()).isNotEqualTo(providerB.id());
        assertThat(providerA.id()).isNotEqualTo(otherDimensions.id());
    }
}
