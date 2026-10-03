package io.github.vfedoriv.graphrag.settings.configuration;

import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.query")
public record QueryProperties(
    @Min(1) int maxRows,
    @Min(1) int timeoutSeconds,
    boolean requireLimit,
    @NotEmpty List<@NotBlank String> blockedKeywords,
    Boolean parentContextExpansionEnabled,
    @Min(1) int parentContextMaxTokens,
    @Min(1) int parentContextMaxParents,
    @Min(1) int parentContextMaxEvidence,
    @Min(1) int parentContextMaxPerDocument,
    @Min(0) int parentContextAdjacentChunks
) {
    @ConstructorBinding
    public QueryProperties {
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

    public QueryProperties(
        int maxRows,
        int timeoutSeconds,
        boolean requireLimit,
        List<String> blockedKeywords
    ) {
        this(
            maxRows,
            timeoutSeconds,
            requireLimit,
            blockedKeywords,
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
