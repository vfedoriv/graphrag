package io.github.vfedoriv.graphrag.ai.domain;

import java.util.Set;

/** Deterministic tokenizer identity resolution shared with document token counting. */
public final class EmbeddingTokenizer {
    private static final Set<String> CL100K_MODELS = Set.of(
        "text-embedding-ada-002", "text-embedding-3-small", "text-embedding-3-large");

    private EmbeddingTokenizer() { }

    public static String resolve(String explicitTokenizerId, String model) {
        if (explicitTokenizerId != null) {
            if (!"cl100k_base".equals(explicitTokenizerId)) {
                throw new IllegalArgumentException("Unsupported explicit tokenizerId: " + explicitTokenizerId);
            }
            return explicitTokenizerId;
        }
        return CL100K_MODELS.contains(model == null ? "" : model.strip()) ? "cl100k_base" : "utf8-byte-v1";
    }
}
