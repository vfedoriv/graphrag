package io.github.vfedoriv.graphrag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotNull @Valid Neo4j neo4j,
    @NotNull @Valid Model model,
    @NotNull @Valid Storage storage,
    @NotNull @Valid Chunking chunking,
    @NotNull @Valid Query query,
    @NotNull @Valid Extraction extraction
) {

    public record Neo4j(
        @NotBlank String database
    ) {
    }

    public record Model(
        @NotBlank String baseUrl,
        String apiKey,
        @NotBlank String embeddingModel,
        @Min(1) int embeddingDimensions,
        @NotBlank String chatModel
    ) {
    }

    public record Storage(
        @NotNull Path documentsRoot
    ) {
    }

    public record Chunking(
        String strategy,
        @Min(1) Integer targetTokens,
        @Min(0) int overlapTokens,
        @Min(1) Integer hardCharacterLimit,
        @Min(1) int maxTokens,
        @Min(1) int maxCharacters,
        @Min(0) Integer contextHeaderMaxTokens,
        @Min(0) Integer contextHeaderMaxCharacters,
        String representationRevision
    ) {
        @ConstructorBinding
        public Chunking {
        }

        public Chunking(int maxTokens, int overlapTokens, int maxCharacters) {
            this(null, null, overlapTokens, null, maxTokens, maxCharacters, 0, 0, "plain-text-v1");
        }

        public String effectiveStrategy() {
            return strategy == null || strategy.isBlank() ? "fixed-character" : strategy.strip();
        }

        public int effectiveTargetTokens() {
            return targetTokens == null ? maxTokens : targetTokens;
        }

        public int effectiveHardCharacterLimit() {
            return hardCharacterLimit == null ? maxCharacters : hardCharacterLimit;
        }

        public int effectiveContextHeaderMaxTokens() {
            return contextHeaderMaxTokens == null ? 96 : contextHeaderMaxTokens;
        }

        public int effectiveContextHeaderMaxCharacters() {
            return contextHeaderMaxCharacters == null ? 512 : contextHeaderMaxCharacters;
        }

        public String effectiveRepresentationRevision() {
            return representationRevision == null || representationRevision.isBlank()
                ? "context-header-v1"
                : representationRevision.strip();
        }
    }

    public record Query(
        @Min(1) int maxRows,
        @Min(1) int timeoutSeconds,
        boolean requireLimit,
        @NotEmpty List<@NotBlank String> blockedKeywords,
        @Min(1) int hybridSearchDefaultTopK,
        @Min(1) int hybridSearchMaxTopK,
        @Min(1) int hybridSearchCandidateMultiplier,
        @Min(1) int hybridSearchMaxCandidates,
        @Min(0) int hybridSearchDefaultGraphDepth,
        @Min(0) int hybridSearchMaxGraphDepth,
        boolean hybridSearchIncludeChunkText,
        Boolean parentContextExpansionEnabled,
        @Min(1) int parentContextMaxTokens,
        @Min(1) int parentContextMaxParents,
        @Min(1) int parentContextMaxEvidence,
        @Min(1) int parentContextMaxPerDocument,
        @Min(0) int parentContextAdjacentChunks
    ) {
        @ConstructorBinding
        public Query {
            if (hybridSearchDefaultTopK == 0) {
                hybridSearchDefaultTopK = 10;
            }
            if (hybridSearchMaxTopK == 0) {
                hybridSearchMaxTopK = 50;
            }
            if (hybridSearchCandidateMultiplier == 0) {
                hybridSearchCandidateMultiplier = 4;
            }
            if (hybridSearchMaxCandidates == 0) {
                hybridSearchMaxCandidates = 200;
            }
            if (hybridSearchMaxGraphDepth == 0 && hybridSearchDefaultGraphDepth > 0) {
                hybridSearchMaxGraphDepth = hybridSearchDefaultGraphDepth;
            }
            if (parentContextExpansionEnabled == null) {
                parentContextExpansionEnabled = true;
            }
            if (parentContextMaxTokens == 0) {
                parentContextMaxTokens = 4096;
            }
            if (parentContextMaxParents == 0) {
                parentContextMaxParents = 8;
            }
            if (parentContextMaxEvidence == 0) {
                parentContextMaxEvidence = 20;
            }
            if (parentContextMaxPerDocument == 0) {
                parentContextMaxPerDocument = 2;
            }
            if (parentContextAdjacentChunks == 0) {
                parentContextAdjacentChunks = 1;
            }
        }

        public Query(
            int maxRows,
            int timeoutSeconds,
            boolean requireLimit,
            List<String> blockedKeywords,
            int hybridSearchDefaultTopK,
            int hybridSearchMaxTopK,
            int hybridSearchCandidateMultiplier,
            int hybridSearchMaxCandidates,
            int hybridSearchDefaultGraphDepth,
            int hybridSearchMaxGraphDepth,
            boolean hybridSearchIncludeChunkText
        ) {
            this(
                maxRows,
                timeoutSeconds,
                requireLimit,
                blockedKeywords,
                hybridSearchDefaultTopK,
                hybridSearchMaxTopK,
                hybridSearchCandidateMultiplier,
                hybridSearchMaxCandidates,
                hybridSearchDefaultGraphDepth,
                hybridSearchMaxGraphDepth,
                hybridSearchIncludeChunkText,
                true,
                4096,
                8,
                20,
                2,
                1
            );
        }

        public boolean effectiveParentContextExpansionEnabled() {
            return Boolean.TRUE.equals(parentContextExpansionEnabled);
        }
    }

    public record Extraction(
        @Min(1) int maxEntitiesPerChunk,
        @Min(1) int maxRelationshipsPerChunk,
        @Min(0) int maxRetries
    ) {
    }
}
