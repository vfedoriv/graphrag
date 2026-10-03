package io.github.vfedoriv.graphrag.documents.application.inspection;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentChunkRevisions;
import io.github.vfedoriv.graphrag.documents.domain.chunking.*;
import io.github.vfedoriv.graphrag.documents.adapters.chunking.TokenizerPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunkRevisionsFacade implements DocumentChunkRevisions {
    @Override
    public String calculate(Settings settings) {
        Map<String, Object> effectiveSettings = new LinkedHashMap<>();
        effectiveSettings.put("hardCharacterLimit", settings.hardCharacterLimit());
        effectiveSettings.put("parentHardCharacterLimit", settings.parentHardCharacterLimit());
        effectiveSettings.put("parentMaxPages", settings.parentMaxPages());
        effectiveSettings.put("parentTargetTokens", settings.parentTargetTokens());
        effectiveSettings.put("contextHeaderMaxCharacters", settings.contextHeaderMaxCharacters());
        effectiveSettings.put("contextHeaderMaxTokens", settings.contextHeaderMaxTokens());
        effectiveSettings.put("overlapTokens", settings.overlapTokens());
        effectiveSettings.put("strategy", settings.strategy());
        effectiveSettings.put("targetTokens", settings.targetTokens());
        ChunkRevisionCalculator calculator = new ChunkRevisionCalculator();
        ChunkSettingsHash settingsHash = calculator.settingsHash(effectiveSettings);
        String strategyRevision = switch (settings.strategy()) {
            case "recursive" -> RecursiveTokenAwareChunkingStrategy.REVISION;
            case "fixed-character" -> new FixedCharacterChunkingStrategy().revision();
            default -> throw new IllegalArgumentException("Unsupported chunking strategy: " + settings.strategy());
        };
        return calculator.chunkerRevision(
            settingsHash,
            strategyRevision,
            TokenizerPolicy.REVISION,
            "parser-policy-v1",
            settings.representationRevision()
        ).value();
    }
}
