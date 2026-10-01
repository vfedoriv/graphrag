package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.document.chunking.TokenizerId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class EmbeddingSpaceIdentity {

    private EmbeddingSpaceIdentity() {
    }

    public static EmbeddingSpace fromProfile(AiProfileNode profile) {
        return derive(
            profile.getBaseUrl(),
            profile.getEmbeddingModel(),
            profile.getEmbeddingDimensions(),
            profile.getTokenizerId()
        );
    }

    public static EmbeddingSpace derive(String baseUrl, String model, int dimensions) {
        return derive(baseUrl, model, dimensions, null);
    }

    public static EmbeddingSpace derive(
        String baseUrl,
        String model,
        int dimensions,
        TokenizerId explicitTokenizerId
    ) {
        EmbeddingTarget target = EmbeddingTarget.derive(baseUrl, model, dimensions,
                explicitTokenizerId == null ? null : explicitTokenizerId.value());
        return new EmbeddingSpace(target.id(), target.normalizedBaseUrl(), target.model(), target.dimensions(), target.tokenizerId());
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
