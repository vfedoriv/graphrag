package io.github.vfedoriv.graphrag.ai.domain;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Non-secret embedding identity; tokenizer remains separate from the historical hash. */
public record EmbeddingTarget(String id, String normalizedBaseUrl, String model, int dimensions, String tokenizerId) {
    public static EmbeddingTarget derive(String baseUrl, String model, int dimensions, String explicitTokenizerId) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String normalizedModel = model == null ? "" : model.strip();
        return new EmbeddingTarget("es_" + sha256(normalizedBaseUrl + "\n" + normalizedModel + "\n" + dimensions),
            normalizedBaseUrl, normalizedModel, dimensions, EmbeddingTokenizer.resolve(explicitTokenizerId, normalizedModel));
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
