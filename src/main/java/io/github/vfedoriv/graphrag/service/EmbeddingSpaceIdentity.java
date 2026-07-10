package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class EmbeddingSpaceIdentity {

    private EmbeddingSpaceIdentity() {
    }

    public static EmbeddingSpace fromProfile(AiProfileNode profile) {
        return derive(profile.getBaseUrl(), profile.getEmbeddingModel(), profile.getEmbeddingDimensions());
    }

    public static EmbeddingSpace derive(String baseUrl, String model, int dimensions) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String normalizedModel = model == null ? "" : model.strip();
        String source = normalizedBaseUrl + "\n" + normalizedModel + "\n" + dimensions;
        return new EmbeddingSpace("es_" + sha256(source), normalizedBaseUrl, normalizedModel, dimensions);
    }

    static String normalizeBaseUrl(String baseUrl) {
        URI uri = URI.create(baseUrl.strip()).normalize();
        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        return scheme + "://" + host + (port < 0 || defaultPort ? "" : ":" + port) + path;
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
