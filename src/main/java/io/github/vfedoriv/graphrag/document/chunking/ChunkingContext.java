package io.github.vfedoriv.graphrag.document.chunking;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChunkingContext(
    String strategyName,
    String strategyRevision,
    int targetTokens,
    int overlapTokens,
    int hardCharacterLimit,
    TokenEstimator tokenEstimator,
    String parserRevision,
    String representationRevision,
    ChunkSettingsHash settingsHash,
    ChunkerRevision effectiveRevision
) {

    public ChunkingContext {
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("strategyName must not be blank");
        }
        if (strategyRevision == null || strategyRevision.isBlank()) {
            throw new IllegalArgumentException("strategyRevision must not be blank");
        }
        if (targetTokens < 1) {
            throw new IllegalArgumentException("targetTokens must be greater than zero");
        }
        if (overlapTokens < 0 || overlapTokens >= targetTokens) {
            throw new IllegalArgumentException("overlapTokens must be non-negative and smaller than targetTokens");
        }
        if (hardCharacterLimit < 1) {
            throw new IllegalArgumentException("hardCharacterLimit must be greater than zero");
        }
        if (tokenEstimator == null || settingsHash == null || effectiveRevision == null) {
            throw new IllegalArgumentException("Chunking revisions and token estimator are required");
        }
    }

    public static ChunkingContext create(
        String strategyName,
        String strategyRevision,
        int targetTokens,
        int overlapTokens,
        int hardCharacterLimit,
        TokenEstimator tokenEstimator,
        String parserRevision,
        String representationRevision
    ) {
        ChunkRevisionCalculator calculator = new ChunkRevisionCalculator();
        Map<String, Object> effectiveSettings = new LinkedHashMap<>();
        effectiveSettings.put("hardCharacterLimit", hardCharacterLimit);
        effectiveSettings.put("overlapTokens", overlapTokens);
        effectiveSettings.put("strategy", strategyName);
        effectiveSettings.put("targetTokens", targetTokens);
        ChunkSettingsHash settingsHash = calculator.settingsHash(effectiveSettings);
        ChunkerRevision revision = calculator.chunkerRevision(
            settingsHash,
            strategyRevision,
            tokenEstimator.revision(),
            parserRevision,
            representationRevision
        );
        return new ChunkingContext(
            strategyName,
            strategyRevision,
            targetTokens,
            overlapTokens,
            hardCharacterLimit,
            tokenEstimator,
            parserRevision,
            representationRevision,
            settingsHash,
            revision
        );
    }
}
