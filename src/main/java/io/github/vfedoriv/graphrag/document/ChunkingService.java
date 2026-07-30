package io.github.vfedoriv.graphrag.document;

import io.github.vfedoriv.graphrag.document.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.document.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.document.chunking.ChunkingStrategy;
import io.github.vfedoriv.graphrag.document.chunking.FixedCharacterChunkingStrategy;
import io.github.vfedoriv.graphrag.document.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.document.chunking.TokenEstimator;
import io.github.vfedoriv.graphrag.document.chunking.TokenizerPolicy;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChunkingService {

    private final RuntimeSettingsService runtimeSettingsService;
    private final TokenizerPolicy tokenizerPolicy;
    private final Map<String, ChunkingStrategy> strategies;

    public ChunkingService(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.tokenizerPolicy = new TokenizerPolicy();
        ChunkingStrategy fixed = new FixedCharacterChunkingStrategy();
        ChunkingStrategy recursive = new RecursiveTokenAwareChunkingStrategy();
        this.strategies = Map.of(fixed.name(), fixed, recursive.name(), recursive);
    }

    public ChunkingContext snapshot(AiProfileNode profile, String parserId) {
        RuntimeSettingsService.ChunkingSettings settings = runtimeSettingsService.chunking();
        ChunkingStrategy strategy = requireStrategy(settings.strategy());
        TokenEstimator estimator = tokenizerPolicy.resolve(profile.getTokenizerId(), profile.getEmbeddingModel());
        return ChunkingContext.create(
            strategy.name(),
            strategy.revision(),
            settings.targetTokens(),
            settings.overlapTokens(),
            settings.hardCharacterLimit(),
            settings.contextHeaderMaxTokens(),
            settings.contextHeaderMaxCharacters(),
            estimator,
            parserRevision(parserId),
            settings.representationRevision()
        );
    }

    public List<ChunkSlice> split(ParsedSection section, ChunkingContext context) {
        List<ChunkSlice> slices = requireStrategy(context.strategyName()).split(section, context);
        log.info(
            "Section chunked: inputLength={}, chunks={}, strategy={}, strategyRevision={}, chunkerRevision={}",
            section == null || section.text() == null ? 0 : section.text().length(),
            slices.size(),
            context.strategyName(),
            context.strategyRevision(),
            context.effectiveRevision().value()
        );
        return slices;
    }

    public List<String> split(String text) {
        RuntimeSettingsService.ChunkingSettings settings = runtimeSettingsService.chunking();
        ChunkingStrategy strategy = requireStrategy(settings.strategy());
        ChunkingContext context = ChunkingContext.create(
            strategy.name(),
            strategy.revision(),
            settings.targetTokens(),
            settings.overlapTokens(),
            settings.hardCharacterLimit(),
            settings.contextHeaderMaxTokens(),
            settings.contextHeaderMaxCharacters(),
            new LegacyCharacterTokenEstimator(),
            "legacy-text-v1",
            settings.representationRevision()
        );
        ParsedSection section = new ParsedSection(0, text, "legacy-text", "TEXT", null, null, Map.of());
        return strategy.split(section, context).stream().map(ChunkSlice::text).toList();
    }

    public int tokenEstimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 4.0);
    }

    private ChunkingStrategy requireStrategy(String name) {
        ChunkingStrategy strategy = strategies.get(name);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported chunking strategy: " + name);
        }
        return strategy;
    }

    private String parserRevision(String parserId) {
        String normalized = parserId == null || parserId.isBlank() ? "unknown-parser" : parserId.strip();
        return normalized + "-v1";
    }

    private final class LegacyCharacterTokenEstimator implements TokenEstimator {

        @Override
        public io.github.vfedoriv.graphrag.document.chunking.TokenizerId tokenizerId() {
            return new io.github.vfedoriv.graphrag.document.chunking.TokenizerId(
                io.github.vfedoriv.graphrag.document.chunking.TokenizerId.UTF8_BYTE_V1
            );
        }

        @Override
        public String revision() {
            return "legacy-character-div4-v1";
        }

        @Override
        public io.github.vfedoriv.graphrag.document.chunking.TokenCountMode countMode() {
            return io.github.vfedoriv.graphrag.document.chunking.TokenCountMode.CONSERVATIVE;
        }

        @Override
        public int count(String text) {
            return tokenEstimate(text);
        }
    }
}
