package io.github.vfedoriv.graphrag.documents.domain.chunking;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChunkingContext(
    String strategyName,
    String strategyRevision,
    int targetTokens,
    int overlapTokens,
    int hardCharacterLimit,
    int parentTargetTokens,
    int parentHardCharacterLimit,
    int parentMaxPages,
    int contextHeaderMaxTokens,
    int contextHeaderMaxCharacters,
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
        if (parentTargetTokens < targetTokens) {
            throw new IllegalArgumentException("parentTargetTokens must not be smaller than targetTokens");
        }
        if (parentHardCharacterLimit < hardCharacterLimit) {
            throw new IllegalArgumentException("parentHardCharacterLimit must not be smaller than hardCharacterLimit");
        }
        if (parentMaxPages < 1 || parentMaxPages > 2) {
            throw new IllegalArgumentException("parentMaxPages must be one or two");
        }
        if (contextHeaderMaxTokens < 0 || contextHeaderMaxCharacters < 0) {
            throw new IllegalArgumentException("Context header limits must not be negative");
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
        return create(
            strategyName,
            strategyRevision,
            targetTokens,
            overlapTokens,
            hardCharacterLimit,
            Math.max(targetTokens, targetTokens * 2),
            Math.max(hardCharacterLimit, hardCharacterLimit * 2),
            2,
            0,
            0,
            tokenEstimator,
            parserRevision,
            representationRevision
        );
    }

    public static ChunkingContext create(
        String strategyName,
        String strategyRevision,
        int targetTokens,
        int overlapTokens,
        int hardCharacterLimit,
        int contextHeaderMaxTokens,
        int contextHeaderMaxCharacters,
        TokenEstimator tokenEstimator,
        String parserRevision,
        String representationRevision
    ) {
        return create(
            strategyName,
            strategyRevision,
            targetTokens,
            overlapTokens,
            hardCharacterLimit,
            Math.max(targetTokens, targetTokens * 2),
            Math.max(hardCharacterLimit, hardCharacterLimit * 2),
            2,
            contextHeaderMaxTokens,
            contextHeaderMaxCharacters,
            tokenEstimator,
            parserRevision,
            representationRevision
        );
    }

    public static ChunkingContext create(
        String strategyName,
        String strategyRevision,
        int targetTokens,
        int overlapTokens,
        int hardCharacterLimit,
        int parentTargetTokens,
        int parentHardCharacterLimit,
        int parentMaxPages,
        int contextHeaderMaxTokens,
        int contextHeaderMaxCharacters,
        TokenEstimator tokenEstimator,
        String parserRevision,
        String representationRevision
    ) {
        ChunkRevisionCalculator calculator = new ChunkRevisionCalculator();
        Map<String, Object> effectiveSettings = new LinkedHashMap<>();
        effectiveSettings.put("hardCharacterLimit", hardCharacterLimit);
        effectiveSettings.put("parentHardCharacterLimit", parentHardCharacterLimit);
        effectiveSettings.put("parentMaxPages", parentMaxPages);
        effectiveSettings.put("parentTargetTokens", parentTargetTokens);
        effectiveSettings.put("contextHeaderMaxCharacters", contextHeaderMaxCharacters);
        effectiveSettings.put("contextHeaderMaxTokens", contextHeaderMaxTokens);
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
            parentTargetTokens,
            parentHardCharacterLimit,
            parentMaxPages,
            contextHeaderMaxTokens,
            contextHeaderMaxCharacters,
            tokenEstimator,
            parserRevision,
            representationRevision,
            settingsHash,
            revision
        );
    }
}
