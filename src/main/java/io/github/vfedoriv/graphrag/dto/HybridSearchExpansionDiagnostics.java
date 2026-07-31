package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record HybridSearchExpansionDiagnostics(
    @Schema(description = "Whether parent-context expansion was enabled.")
    boolean enabled,
    @Schema(description = "Number of ranked child evidence records considered.")
    int evidenceConsidered,
    @Schema(description = "Number of unique expanded contexts included.")
    int contextCount,
    @Schema(description = "Estimated tokens in included expanded context.")
    int contextTokenEstimate,
    @Schema(description = "Content-free validation and budget outcome counts.")
    Map<String, Integer> outcomes,
    @Schema(description = "Expansion execution time in milliseconds.")
    long executionTimeMs
) {
    public HybridSearchExpansionDiagnostics {
        outcomes = outcomes == null ? Map.of() : Map.copyOf(outcomes);
    }

    public static HybridSearchExpansionDiagnostics disabled() {
        return new HybridSearchExpansionDiagnostics(false, 0, 0, 0, Map.of("DISABLED", 1), 0);
    }
}
