package io.github.vfedoriv.graphrag.ai.domain;

import java.util.List;

public final class EmbeddingCompatibilityRule {
    private EmbeddingCompatibilityRule() { }

    public static boolean compatible(EmbeddingTarget target, List<StoredEmbeddingObservation> observations) {
        for (StoredEmbeddingObservation observation : observations) {
            String tokenizer = observation.tokenizerId();
            if (tokenizer == null || tokenizer.isBlank()) {
                tokenizer = EmbeddingTokenizer.resolve(null, observation.embeddingModel());
            }
            if (!target.id().equals(observation.embeddingSpaceId()) || !target.tokenizerId().equals(tokenizer)) {
                return false;
            }
        }
        return true;
    }
}
