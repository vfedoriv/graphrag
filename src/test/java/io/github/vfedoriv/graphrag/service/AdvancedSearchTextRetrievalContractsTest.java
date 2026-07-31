package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdvancedSearchTextRetrievalContractsTest {

    @Test
    void boundsSubqueriesAndCandidates() {
        assertThatThrownBy(() -> new Request(
            "kb-1",
            List.of(new Subquery("q-1", "text")),
            new MetadataConstraints(null, null),
            201,
            true,
            Instant.now().plusSeconds(10)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("candidateLimit");

        List<Subquery> tooMany = java.util.stream.IntStream.range(0, 9)
            .mapToObj(index -> new Subquery("q-" + index, "text-" + index))
            .toList();
        assertThatThrownBy(() -> new Request(
            "kb-1", tooMany, null, 10, false, Instant.now().plusSeconds(10)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("subqueries");
    }

    @Test
    void sanitizesFailureDiagnosticsWithoutRetainingContent() {
        Diagnostics diagnostics = new Diagnostics(Status.FAILED, 1, 0, "Provider failure: private query text");

        assertThat(diagnostics.failureCategory()).isEqualTo("Provider_failure__private_query_text");
    }
}
