package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphArtifactCleanupServiceTest {

    @Test
    void mapsDocumentCleanupCounts() {
        GraphArtifactCleanupService.DocumentArtifactCleanupResult result =
            GraphArtifactCleanupService.documentCleanupResult(Map.of(
                "deletedChunks", 2,
                "deletedRuns", 3L,
                "deletedRelationships", 4,
                "deletedObsoleteExtractedNodes", 5
            ));

        assertThat(result.deletedChunks()).isEqualTo(2L);
        assertThat(result.deletedRuns()).isEqualTo(3L);
        assertThat(result.deletedRelationships()).isEqualTo(4L);
        assertThat(result.deletedObsoleteExtractedNodes()).isEqualTo(5L);
    }

    @Test
    void mapsExtractionCleanupCounts() {
        GraphArtifactCleanupService.ExtractionRunCleanupResult result =
            GraphArtifactCleanupService.extractionRunCleanupResult(Map.of(
                "deletedRuns", 1,
                "deletedRelationships", 2,
                "deletedObsoleteExtractedNodes", 3
            ));

        assertThat(result.deletedRuns()).isEqualTo(1L);
        assertThat(result.deletedRelationships()).isEqualTo(2L);
        assertThat(result.deletedObsoleteExtractedNodes()).isEqualTo(3L);
    }

    @Test
    void mapsNullRowsToZeroCounts() {
        GraphArtifactCleanupService.DocumentArtifactCleanupResult documentResult =
            GraphArtifactCleanupService.documentCleanupResult(null);
        GraphArtifactCleanupService.ExtractionRunCleanupResult extractionResult =
            GraphArtifactCleanupService.extractionRunCleanupResult(null);

        assertThat(documentResult.deletedChunks()).isZero();
        assertThat(documentResult.deletedRuns()).isZero();
        assertThat(documentResult.deletedRelationships()).isZero();
        assertThat(documentResult.deletedObsoleteExtractedNodes()).isZero();
        assertThat(extractionResult.deletedRuns()).isZero();
        assertThat(extractionResult.deletedRelationships()).isZero();
        assertThat(extractionResult.deletedObsoleteExtractedNodes()).isZero();
    }
}
