package io.github.vfedoriv.graphrag.settings.contracts;

import java.time.Duration;
import java.util.List;

/** Typed snapshots for consumers; management and override state remain settings-owned. */
public interface RuntimeSettingsAccess {
    QuerySettings query();
    ChunkingSettings chunking();
    ExtractionSettings extraction();
    DiscoverySettings discovery();
    AiObservationSettings aiObservation();
    AdvancedSearchSettings advancedSearch();
    boolean modelNameTagEnabled();
    String effectiveChunkerRevision();
    List<ChunkingSettingFact> chunkingFacts();
    String documentStorageRootValue();

    record ChunkingSettingFact(String key, String source, Object currentValue, String chunkMigrationLifecycle) {
        public ChunkingSettingFact {
            if (currentValue != null && !(currentValue instanceof String) && !(currentValue instanceof Integer)) {
                throw new IllegalArgumentException("Chunking facts accept only immutable string or integer values");
            }
        }
    }

    record QuerySettings(
        int maxRows,
        int timeoutSeconds,
        boolean requireLimit,
        List<String> blockedKeywords,
        boolean parentContextExpansionEnabled,
        int parentContextMaxTokens,
        int parentContextMaxParents,
        int parentContextMaxEvidence,
        int parentContextMaxPerDocument,
        int parentContextAdjacentChunks
    ) {
        public QuerySettings { blockedKeywords = List.copyOf(blockedKeywords); }
    }

    record ChunkingSettings(
        String strategy,
        int targetTokens,
        int overlapTokens,
        int hardCharacterLimit,
        int parentTargetTokens,
        int parentHardCharacterLimit,
        int parentMaxPages,
        int contextHeaderMaxTokens,
        int contextHeaderMaxCharacters,
        String representationRevision
    ) {
        public int maxTokens() {
            return targetTokens;
        }

        public int maxCharacters() {
            return hardCharacterLimit;
        }
    }

    record ExtractionSettings(int maxEntitiesPerChunk, int maxRelationshipsPerChunk, int maxRetries) {
    }

    record DiscoverySettings(
        int maxSources,
        int maxSourceBytes,
        int maxTotalBytes,
        int maxSourceCharacters,
        int maxTotalCharacters,
        int chunkCharacters,
        int maxChunksPerSource,
        int maxConcurrency,
        Duration sourceTimeout,
        Duration requestTimeout
    ) {
    }

    record AiObservationSettings(
        boolean enabled,
        boolean contentCaptureEnabled,
        int maxAttributeLength,
        boolean inputOutputContentEnabled,
        int maxInputOutputLength,
        boolean modelNameTagEnabled,
        boolean schemaNameTagEnabled
    ) {
    }

    record AdvancedSearchSettings(
        Duration deadline,
        int defaultEvidence,
        boolean defaultIncludeEvidenceText,
        int maxEvidence,
        int candidateLimit,
        int maxCandidates,
        int rerankPoolSize,
        int graphExpansionSeedLimit,
        int graphExpansionFactLimit,
        int maxQueryLength,
        int maxEvidenceTextCharacters,
        int planningMaxSubqueries,
        int planningMaxExactTerms,
        int planningMaxGraphRequests,
        int planningMaxStringCharacters,
        int planningEvaluationEvidenceLimit,
        int planningEvidenceExcerptCharacters,
        int followUpMaxQueries,
        Duration followUpMinimumRemaining,
        Duration synthesisReserve,
        Duration retention,
        int cleanupBatchSize
    ) { }
}
