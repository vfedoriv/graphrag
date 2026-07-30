package io.github.vfedoriv.graphrag.document.chunking;

import java.util.Set;

public final class TokenizerPolicy {

    public static final String REVISION = "tokenizer-policy-v1";
    private static final Set<String> CL100K_MODELS = Set.of(
        "text-embedding-ada-002",
        "text-embedding-3-small",
        "text-embedding-3-large"
    );

    public TokenEstimator resolve(TokenizerId explicitTokenizerId, String embeddingModel) {
        if (explicitTokenizerId != null) {
            if (TokenizerId.CL100K_BASE.equals(explicitTokenizerId.value())) {
                return new Cl100kTokenEstimator();
            }
            throw new IllegalArgumentException("Unsupported explicit tokenizerId: " + explicitTokenizerId.value());
        }
        String normalizedModel = embeddingModel == null ? "" : embeddingModel.strip();
        if (CL100K_MODELS.contains(normalizedModel)) {
            return new Cl100kTokenEstimator();
        }
        return new Utf8ByteTokenEstimator();
    }

    public TokenizerId validateExplicit(String value) {
        return value == null ? null : TokenizerId.explicit(value);
    }
}
