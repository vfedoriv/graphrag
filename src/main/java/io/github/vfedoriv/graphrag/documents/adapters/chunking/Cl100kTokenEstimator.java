package io.github.vfedoriv.graphrag.documents.adapters.chunking;

import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenCountMode;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenEstimator;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenizerId;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

public final class Cl100kTokenEstimator implements TokenEstimator {

    public static final String REVISION = "cl100k-base-jtokkit-1.1.0";
    private static final Encoding ENCODING =
        Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Override
    public TokenizerId tokenizerId() {
        return new TokenizerId(TokenizerId.CL100K_BASE);
    }

    @Override
    public String revision() {
        return REVISION;
    }

    @Override
    public TokenCountMode countMode() {
        return TokenCountMode.EXACT;
    }

    @Override
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : ENCODING.countTokensOrdinary(text);
    }
}
