package io.github.vfedoriv.graphrag.documents.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public final class ChunkingStateDtos {
    private ChunkingStateDtos() { }

    @Schema(name = "ChunkingState")
    public record ChunkingStateResponse(
        String strategy,
        int targetTokens,
        int overlapTokens,
        int hardCharacterLimit,
        int parentTargetTokens,
        int parentHardCharacterLimit,
        int parentMaxPages,
        int contextHeaderMaxTokens,
        int contextHeaderMaxCharacters,
        String representationRevision,
        Map<String, String> valueSources,
        ComponentRevisions componentRevisions,
        String tokenizerId,
        String tokenizerRevision,
        String tokenCountMode,
        String parserPolicyRevision,
        String settingsHash,
        String effectiveChunkerRevision,
        String migrationLifecycle,
        List<CompatibilityAlias> compatibilityAliases
    ) { }

    public record ComponentRevisions(
        String strategyRevision,
        String tokenizerPolicyRevision,
        String tokenizerRevision,
        String parserPolicyRevision,
        String representationRevision
    ) { }

    @Schema(name = "ChunkingCompatibilityAlias")
    public record CompatibilityAlias(
        String aliasKey,
        String canonicalKey,
        Object configuredValue,
        Object effectiveValue,
        boolean authoritative,
        String precedence
    ) { }
}
