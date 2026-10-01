package io.github.vfedoriv.graphrag.documents.domain.chunking;

public record TokenizerId(String value) {

    public static final String CL100K_BASE = "cl100k_base";
    public static final String UTF8_BYTE_V1 = "utf8-byte-v1";

    public TokenizerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tokenizerId must not be blank");
        }
        value = value.strip();
        if (!CL100K_BASE.equals(value) && !UTF8_BYTE_V1.equals(value)) {
            throw new IllegalArgumentException("Unsupported tokenizerId: " + value);
        }
    }

    public static TokenizerId explicit(String value) {
        TokenizerId tokenizerId = new TokenizerId(value);
        if (UTF8_BYTE_V1.equals(tokenizerId.value())) {
            throw new IllegalArgumentException("Unsupported explicit tokenizerId: " + value);
        }
        return tokenizerId;
    }

    @Override
    public String toString() {
        return value;
    }
}
