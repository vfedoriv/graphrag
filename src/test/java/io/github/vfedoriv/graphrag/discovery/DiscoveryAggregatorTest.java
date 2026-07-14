package io.github.vfedoriv.graphrag.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.Candidate;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.CandidateKind;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.ConflictCategory;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.Evidence;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.EvidenceOrigin;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.ReviewState;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.ConceptRule;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.DiscoveryGuidance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscoveryAggregatorTest {

    private final DiscoveryAggregator aggregator = new DiscoveryAggregator(new ObjectMapper());

    @Test
    void unionsEvidenceCountsIndependentDocumentsAndIsOrderIndependent() {
        Candidate first = property("STRING", "source-a", "document-a", "chunk-1");
        Candidate repeatedChunk = property("STRING", "source-a", "document-a", "chunk-2");
        Candidate secondDocument = property("STRING", "source-b", "document-b", "chunk-1");
        List<DiscoverySourceAnalyzer.SourceAnalysis> analyses = new ArrayList<>(List.of(
            analysis("source-a", "document-a", List.of(first, repeatedChunk)),
            analysis("source-b", "document-b", List.of(secondDocument))
        ));

        DiscoveryAggregator.AggregateResult forward = aggregator.aggregate(analyses, request(DiscoveryGuidance.empty()));
        Collections.reverse(analyses);
        DiscoveryAggregator.AggregateResult reverse = aggregator.aggregate(analyses, request(DiscoveryGuidance.empty()));

        Candidate candidate = forward.candidates().stream().filter(value -> value.kind() == CandidateKind.NODE_PROPERTY).findFirst().orElseThrow();
        assertThat(candidate.supportCount()).isEqualTo(2);
        assertThat(candidate.evidence()).hasSize(3);
        assertThat(forward).isEqualTo(reverse);
    }

    @Test
    void retainsCompetingPropertyTypesInsteadOfSelectingFirstValue() {
        List<DiscoverySourceAnalyzer.SourceAnalysis> analyses = List.of(
            analysis("source-a", "document-a", List.of(property("STRING", "source-a", "document-a", "chunk-1"))),
            analysis("source-b", "document-b", List.of(property("DATE", "source-b", "document-b", "chunk-1")))
        );

        DiscoveryAggregator.AggregateResult result = aggregator.aggregate(analyses, request(DiscoveryGuidance.empty()));

        assertThat(result.candidates()).filteredOn(value -> value.kind() == CandidateKind.NODE_PROPERTY)
            .extracting(Candidate::propertyType).containsExactly("DATE", "STRING");
        assertThat(result.conflicts()).anySatisfy(conflict -> {
            assertThat(conflict.category()).isEqualTo(ConflictCategory.PROPERTY_TYPE);
            assertThat(conflict.alternatives()).containsExactly("DATE", "STRING");
        });
        assertThat(result.schema().at("/nodes/0/properties").isEmpty()).isTrue();
    }

    @Test
    void requiredExcludedAndPreferredGuidanceStayReviewableAndDeterministic() {
        DiscoveryGuidance guidance = new DiscoveryGuidance(null, List.of(),
            List.of(new ConceptRule("Invoice", null, List.of("invoiceId"))),
            List.of(new ConceptRule("Payment", null, List.of())),
            List.of("Secret"), null, List.of(), List.of());
        Candidate excluded = node("Secret", "source-a", "document-a");

        DiscoveryAggregator.AggregateResult result = aggregator.aggregate(
            List.of(analysis("source-a", "document-a", List.of(excluded))), request(guidance));

        assertThat(result.candidates()).anySatisfy(candidate -> {
            if (candidate.identity().equals("node:Invoice")) {
                assertThat(candidate.origins()).contains(EvidenceOrigin.GUIDED);
                assertThat(candidate.reviewState()).isEqualTo(ReviewState.REVIEW_REQUIRED);
            }
        });
        assertThat(result.candidates()).filteredOn(candidate -> candidate.identity().equals("node:Secret"))
            .allSatisfy(candidate -> assertThat(candidate.reviewState()).isEqualTo(ReviewState.SUPPRESSED));
        assertThat(result.warnings()).extracting(value -> value.code())
            .contains("REQUIRED_CONCEPT_UNSUPPORTED", "PREFERRED_CONCEPT_UNSUPPORTED", "EXCLUDED_CONCEPT_SUPPRESSED");
        assertThat(result.schema().toString()).doesNotContain("Secret");
    }

    private Candidate property(String type, String source, String document, String chunk) {
        Evidence evidence = new Evidence(source, source + "-fingerprint", chunk, document, Set.of(EvidenceOrigin.OBSERVED));
        return new Candidate(CandidateKind.NODE_PROPERTY, "node-property:Person:birthDate", "Person", "birthDate", type,
            List.of(), null, null, null, "Person", "birth_date", null, 0.8, Set.of(EvidenceOrigin.OBSERVED),
            List.of(evidence), 1, ReviewState.RECOMMENDED);
    }

    private Candidate node(String label, String source, String document) {
        Evidence evidence = new Evidence(source, source + "-fingerprint", "chunk-1", document, Set.of(EvidenceOrigin.OBSERVED));
        return new Candidate(CandidateKind.NODE, "node:" + label, label, null, null, List.of(), null, null, null,
            label, null, null, 0.8, Set.of(EvidenceOrigin.OBSERVED), List.of(evidence), 1, ReviewState.RECOMMENDED);
    }

    private DiscoverySourceAnalyzer.SourceAnalysis analysis(String sourceId, String documentId, List<Candidate> candidates) {
        PreparedDiscoverySource source = new PreparedDiscoverySource(sourceId, DiscoveryContracts.SourceType.DOCUMENT,
            sourceId, documentId, sourceId + "-fingerprint", List.of());
        return new DiscoverySourceAnalyzer.SourceAnalysis(source, candidates, List.of());
    }

    private SchemaDiscoveryRequest request(DiscoveryGuidance guidance) {
        return new SchemaDiscoveryRequest(List.of(), List.of(), null, guidance);
    }
}
