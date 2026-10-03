package io.github.vfedoriv.graphrag.documents.application.management;

import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.ai.contracts.StartupModelMetadata;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkRevisionCalculator;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSettingsHash;
import io.github.vfedoriv.graphrag.documents.domain.chunking.FixedCharacterChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenEstimator;
import io.github.vfedoriv.graphrag.documents.adapters.chunking.TokenizerPolicy;
import io.github.vfedoriv.graphrag.documents.api.model.ChunkingStateDtos.ChunkingStateResponse;
import io.github.vfedoriv.graphrag.documents.api.model.ChunkingStateDtos.CompatibilityAlias;
import io.github.vfedoriv.graphrag.documents.api.model.ChunkingStateDtos.ComponentRevisions;
import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess.ChunkingSettingFact;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ChunkingStateService {
    private static final String PARSER_POLICY_REVISION = "parser-policy-v1";
    private static final String TARGET_TOKENS = "app.chunking.target-tokens";
    private static final String TARGET_TOKENS_ALIAS = "app.chunking.max-tokens";
    private static final String HARD_LIMIT = "app.chunking.hard-character-limit";
    private static final String HARD_LIMIT_ALIAS = "app.chunking.max-characters";

    private final RuntimeSettingsAccess runtimeSettingsService;
    private final StartupModelMetadata appProperties;

    public ChunkingStateService(RuntimeSettingsAccess runtimeSettingsService, StartupModelMetadata appProperties) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.appProperties = appProperties;
    }

    @RelationalTransactional(readOnly = true)
    public ChunkingStateResponse get() {
        RuntimeSettingsAccess.ChunkingSettings settings = runtimeSettingsService.chunking();
        List<ChunkingSettingFact> rows = runtimeSettingsService.chunkingFacts();
        Map<String, ChunkingSettingFact> byKey = rows.stream()
            .collect(java.util.stream.Collectors.toMap(ChunkingSettingFact::key, value -> value));

        Map<String, Object> effectiveValues = new LinkedHashMap<>();
        effectiveValues.put("hardCharacterLimit", settings.hardCharacterLimit());
        effectiveValues.put("parentHardCharacterLimit", settings.parentHardCharacterLimit());
        effectiveValues.put("parentMaxPages", settings.parentMaxPages());
        effectiveValues.put("parentTargetTokens", settings.parentTargetTokens());
        effectiveValues.put("contextHeaderMaxCharacters", settings.contextHeaderMaxCharacters());
        effectiveValues.put("contextHeaderMaxTokens", settings.contextHeaderMaxTokens());
        effectiveValues.put("overlapTokens", settings.overlapTokens());
        effectiveValues.put("strategy", settings.strategy());
        effectiveValues.put("targetTokens", settings.targetTokens());
        ChunkRevisionCalculator calculator = new ChunkRevisionCalculator();
        ChunkSettingsHash settingsHash = calculator.settingsHash(effectiveValues);
        String strategyRevision = strategyRevision(settings.strategy());
        TokenEstimator tokenizer = new TokenizerPolicy().resolve(null, appProperties.embeddingModel());
        String effectiveRevision = calculator.chunkerRevision(settingsHash, strategyRevision, TokenizerPolicy.REVISION,
            PARSER_POLICY_REVISION, settings.representationRevision()).value();

        Map<String, String> sources = new LinkedHashMap<>();
        for (String key : List.of(
            TARGET_TOKENS, "app.chunking.overlap-tokens", HARD_LIMIT,
            "app.chunking.parent-target-tokens", "app.chunking.parent-hard-character-limit",
            "app.chunking.parent-max-pages", "app.chunking.context-header-max-tokens",
            "app.chunking.context-header-max-characters", "app.chunking.strategy",
            "app.chunking.representation-revision"
        )) {
            ChunkingSettingFact row = byKey.get(key);
            if (row != null) {
                sources.put(key, row.source());
            }
        }
        if ("default".equals(sources.get(TARGET_TOKENS))
            && "override".equals(source(byKey, TARGET_TOKENS_ALIAS))) {
            sources.put(TARGET_TOKENS, "compatibility-alias");
        }
        if ("default".equals(sources.get(HARD_LIMIT))
            && "override".equals(source(byKey, HARD_LIMIT_ALIAS))) {
            sources.put(HARD_LIMIT, "compatibility-alias");
        }
        String lifecycle = rows.stream()
            .map(ChunkingSettingFact::chunkMigrationLifecycle)
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElse("explicit-reprocessing-required");

        return new ChunkingStateResponse(
            settings.strategy(), settings.targetTokens(), settings.overlapTokens(), settings.hardCharacterLimit(),
            settings.parentTargetTokens(), settings.parentHardCharacterLimit(), settings.parentMaxPages(),
            settings.contextHeaderMaxTokens(), settings.contextHeaderMaxCharacters(), settings.representationRevision(),
            Map.copyOf(sources),
            new ComponentRevisions(
                strategyRevision, TokenizerPolicy.REVISION, tokenizer.revision(), PARSER_POLICY_REVISION,
                settings.representationRevision()
            ),
            tokenizer.tokenizerId().value(), tokenizer.revision(), tokenizer.countMode().name(),
            PARSER_POLICY_REVISION, settingsHash.value(), effectiveRevision, lifecycle,
            List.of(
                alias(byKey, TARGET_TOKENS_ALIAS, TARGET_TOKENS, settings.targetTokens()),
                alias(byKey, HARD_LIMIT_ALIAS, HARD_LIMIT, settings.hardCharacterLimit())
            )
        );
    }

    private String source(Map<String, ChunkingSettingFact> rows, String key) {
        ChunkingSettingFact row = rows.get(key);
        return row == null ? "default" : row.source();
    }

    private CompatibilityAlias alias(
        Map<String, ChunkingSettingFact> rows, String aliasKey, String canonicalKey, Object effectiveValue
    ) {
        ChunkingSettingFact alias = rows.get(aliasKey);
        ChunkingSettingFact canonical = rows.get(canonicalKey);
        boolean canonicalOverride = canonical != null && "override".equals(canonical.source());
        boolean aliasOverride = alias != null && "override".equals(alias.source());
        return new CompatibilityAlias(
            aliasKey,
            canonicalKey,
            aliasOverride && alias != null ? alias.currentValue() : null,
            effectiveValue,
            canonicalOverride,
            canonicalOverride ? "canonical-key" : aliasOverride ? "compatibility-alias" : "default"
        );
    }

    private String strategyRevision(String strategy) {
        return switch (strategy) {
            case "recursive" -> RecursiveTokenAwareChunkingStrategy.REVISION;
            case "fixed-character" -> new FixedCharacterChunkingStrategy().revision();
            default -> throw new IllegalArgumentException("Unsupported chunking strategy: " + strategy);
        };
    }
}
