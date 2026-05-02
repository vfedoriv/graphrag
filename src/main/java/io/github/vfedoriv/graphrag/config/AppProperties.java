package io.github.vfedoriv.graphrag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
        @Min(1) int maxTokens,
        @Min(0) int overlapTokens,
        @Min(1) int maxCharacters
    ) {
    }

    public record Query(
        @Min(1) int maxRows,
        @Min(1) int timeoutSeconds,
        boolean requireLimit,
        @NotEmpty List<@NotBlank String> blockedKeywords
    ) {
    }

    public record Extraction(
        @Min(1) int maxEntitiesPerChunk,
        @Min(1) int maxRelationshipsPerChunk,
        @Min(0) int maxRetries
    ) {
    }
}
