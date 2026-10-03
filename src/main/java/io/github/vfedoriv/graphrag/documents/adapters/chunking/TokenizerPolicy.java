package io.github.vfedoriv.graphrag.documents.adapters.chunking;

import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenEstimator;
import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;
import io.github.vfedoriv.graphrag.documents.domain.chunking.Utf8ByteTokenEstimator;

public final class TokenizerPolicy {

    public static final String REVISION = "tokenizer-policy-v1";
    public TokenEstimator resolve(TokenizerId explicitTokenizerId, String embeddingModel) {
        String resolved = io.github.vfedoriv.graphrag.ai.domain.EmbeddingTokenizer.resolve(
            explicitTokenizerId == null ? null : explicitTokenizerId.value(), embeddingModel);
        return TokenizerId.CL100K_BASE.equals(resolved) ? new Cl100kTokenEstimator() : new Utf8ByteTokenEstimator();
    }

    public TokenizerId validateExplicit(String value) {
        return value == null ? null : TokenizerId.explicit(value);
    }
}
