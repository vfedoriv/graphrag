package io.github.vfedoriv.graphrag.documents.domain.chunking;

import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;

import java.nio.charset.StandardCharsets;

public final class Utf8ByteTokenEstimator implements TokenEstimator {

    public static final String REVISION = "utf8-byte-v1";

    @Override
    public TokenizerId tokenizerId() {
        return new TokenizerId(TokenizerId.UTF8_BYTE_V1);
    }

    @Override
    public String revision() {
        return REVISION;
    }

    @Override
    public TokenCountMode countMode() {
        return TokenCountMode.CONSERVATIVE;
    }

    @Override
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }
}
