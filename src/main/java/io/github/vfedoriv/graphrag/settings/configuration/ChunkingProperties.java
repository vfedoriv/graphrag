package io.github.vfedoriv.graphrag.settings.configuration;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.chunking")
public record ChunkingProperties(
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
    public ChunkingProperties {
    }

    public ChunkingProperties(int maxTokens, int overlapTokens, int maxCharacters) {
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
