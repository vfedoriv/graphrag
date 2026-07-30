package io.github.vfedoriv.graphrag.document.chunking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class ChunkRevisionCalculator {

    public ChunkSettingsHash settingsHash(Map<String, ?> settings) {
        return new ChunkSettingsHash(sha256(canonical(settings)));
    }

    public ChunkerRevision chunkerRevision(
        ChunkSettingsHash settingsHash,
        String strategyRevision,
        String tokenizerRevision,
        String parserRevision,
        String representationRevision
    ) {
        Map<String, Object> inputs = Map.of(
            "parserRevision", required(parserRevision, "parserRevision"),
            "representationRevision", required(representationRevision, "representationRevision"),
            "settingsHash", settingsHash.value(),
            "strategyRevision", required(strategyRevision, "strategyRevision"),
            "tokenizerRevision", required(tokenizerRevision, "tokenizerRevision")
        );
        return new ChunkerRevision("chunker_" + sha256(canonical(inputs)));
    }

    private String canonical(Map<String, ?> values) {
        TreeMap<String, ?> sorted = new TreeMap<>(values);
        StringBuilder canonical = new StringBuilder();
        sorted.forEach((key, value) -> {
            String text = String.valueOf(value);
            canonical.append(key.length()).append(':').append(key)
                .append('=').append(text.length()).append(':').append(text).append('\n');
        });
        return canonical.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
