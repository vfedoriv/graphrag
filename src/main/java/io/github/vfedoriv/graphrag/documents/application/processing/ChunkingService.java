package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;

import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.FixedCharacterChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenEstimator;
import io.github.vfedoriv.graphrag.documents.adapters.chunking.TokenizerPolicy;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.service.ChunkMigrationSnapshot;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
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
        ChunkingStrategy recursive = new RecursiveTokenAwareChunkingStrategy((text, referenceContext) ->
            new io.github.vfedoriv.graphrag.documents.adapters.chunking.LangChain4jRecursiveSplitterAdapter(referenceContext)
                .referenceSegmentCount(text));
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
            settings.parentTargetTokens(),
            settings.parentHardCharacterLimit(),
            settings.parentMaxPages(),
            settings.contextHeaderMaxTokens(),
            settings.contextHeaderMaxCharacters(),
            estimator,
            parserRevision(parserId),
            settings.representationRevision()
        );
    }

    public String migrationTargetRevision(AiProfileNode profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        return runtimeSettingsService.effectiveChunkerRevision();
    }

    public String migrationTargetRevision() {
        return runtimeSettingsService.effectiveChunkerRevision();
    }

    public ChunkMigrationSnapshot.ChunkTarget snapshotTarget(AiProfileNode profile) {
        ChunkingContext context = snapshot(profile, "migration-target");
        return new ChunkMigrationSnapshot.ChunkTarget(
            context.strategyName(),
            context.strategyRevision(),
            context.targetTokens(),
            context.overlapTokens(),
            context.hardCharacterLimit(),
            context.parentTargetTokens(),
            context.parentHardCharacterLimit(),
            context.parentMaxPages(),
            context.contextHeaderMaxTokens(),
            context.contextHeaderMaxCharacters(),
            context.tokenEstimator().tokenizerId().value(),
            context.tokenEstimator().revision(),
            context.tokenEstimator().countMode().name(),
            context.representationRevision(),
            context.settingsHash().value()
        );
    }

    public ChunkingContext restore(
        AiProfileNode profile,
        ChunkMigrationSnapshot.ChunkTarget target,
        ChunkMigrationSnapshot.DocumentTarget documentTarget
    ) {
        return restore(profile, new DocumentReprocessing.ChunkTarget(
            target.strategyName(), target.strategyRevision(), target.targetTokens(), target.overlapTokens(),
            target.hardCharacterLimit(), target.parentTargetTokens(), target.parentHardCharacterLimit(),
            target.parentMaxPages(), target.contextHeaderMaxTokens(), target.contextHeaderMaxCharacters(),
            target.tokenizerId(), target.tokenizerRevision(), target.tokenCountMode(),
            target.representationRevision(), target.settingsHash()), new DocumentReprocessing.DocumentTarget(
            documentTarget.sourceSha256(), documentTarget.parserId(), documentTarget.parserRevision(),
            documentTarget.fileFormat(), documentTarget.effectiveChunkerRevision(), documentTarget.effectiveProcessingOptions()));
    }

    public ChunkingContext restore(
        AiProfileNode profile,
        DocumentReprocessing.ChunkTarget target,
        DocumentReprocessing.DocumentTarget documentTarget
    ) {
        TokenEstimator estimator = tokenizerPolicy.resolve(profile.getTokenizerId(), profile.getEmbeddingModel());
        requireSnapshotIdentity(target, estimator);
        ChunkingContext restored = ChunkingContext.create(
            target.strategyName(),
            target.strategyRevision(),
            target.targetTokens(),
            target.overlapTokens(),
            target.hardCharacterLimit(),
            target.parentTargetTokens(),
            target.parentHardCharacterLimit(),
            target.parentMaxPages(),
            target.contextHeaderMaxTokens(),
            target.contextHeaderMaxCharacters(),
            estimator,
            documentTarget.parserRevision(),
            target.representationRevision()
        );
        if (!target.settingsHash().equals(restored.settingsHash().value())
            || !documentTarget.effectiveChunkerRevision().equals(restored.effectiveRevision().value())) {
            throw new IllegalStateException("Immutable chunk migration snapshot does not reproduce its target revision");
        }
        return restored;
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
            settings.parentTargetTokens(),
            settings.parentHardCharacterLimit(),
            settings.parentMaxPages(),
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

    private void requireSnapshotIdentity(
        DocumentReprocessing.ChunkTarget target,
        TokenEstimator estimator
    ) {
        if (!target.tokenizerId().equals(estimator.tokenizerId().value())
            || !target.tokenizerRevision().equals(estimator.revision())
            || !target.tokenCountMode().equals(estimator.countMode().name())) {
            throw new IllegalStateException("AI profile no longer resolves the snapshotted tokenizer target");
        }
        requireStrategy(target.strategyName());
    }

    private final class LegacyCharacterTokenEstimator implements TokenEstimator {

        @Override
        public io.github.vfedoriv.graphrag.documents.domain.chunking.TokenizerId tokenizerId() {
            return new io.github.vfedoriv.graphrag.documents.domain.chunking.TokenizerId(
                io.github.vfedoriv.graphrag.documents.domain.chunking.TokenizerId.UTF8_BYTE_V1
            );
        }

        @Override
        public String revision() {
            return "legacy-character-div4-v1";
        }

        @Override
        public io.github.vfedoriv.graphrag.documents.domain.chunking.TokenCountMode countMode() {
            return io.github.vfedoriv.graphrag.documents.domain.chunking.TokenCountMode.CONSERVATIVE;
        }

        @Override
        public int count(String text) {
            return tokenEstimate(text);
        }
    }
}
