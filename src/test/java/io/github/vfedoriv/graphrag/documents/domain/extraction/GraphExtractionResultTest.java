package io.github.vfedoriv.graphrag.documents.domain.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphExtractionResultTest {

    @Test
    void normalizesNullCollectionsToEmptyLists() {
        GraphExtractionResult result = new GraphExtractionResult(null, null);

        assertThat(result.nodes()).isEmpty();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void preservesPopulatedCollections() {
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 0.95)),
            List.of()
        );

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().getFirst().label()).isEqualTo("Contract");
    }
}
