package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.Channel;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.service.AdvancedSearchFusionService.FusionOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedSearchFusionServiceTest {

    private final AdvancedSearchFusionService service = new AdvancedSearchFusionService();

    @Test
    void fusionIsDeterministicAndDeduplicatesChunkContributions() {
        Result dense = result(Branch.DENSE, List.of(
            candidate(Branch.DENSE, "shared", "doc-1", "q-1", 1, 0.99),
            candidate(Branch.DENSE, "dense-only", "doc-2", "q-1", 2, 0.98)
        ));
        Result lexical = result(Branch.LEXICAL, List.of(
            candidate(Branch.LEXICAL, "shared", "doc-1", "q-1", 1, 100.0),
            candidate(Branch.LEXICAL, "lexical-only", "doc-3", "q-1", 2, 1.0)
        ));
        FusionOptions options = FusionOptions.defaults();

        List<EvidenceCandidate> forward = service.fuse(List.of(dense, lexical), null, options).candidates();
        List<EvidenceCandidate> reversed = service.fuse(List.of(lexical, dense), null, options).candidates();

        assertThat(forward).extracting(EvidenceCandidate::chunkId)
            .containsExactlyElementsOf(reversed.stream().map(EvidenceCandidate::chunkId).toList());
        assertThat(forward).extracting(EvidenceCandidate::fusedScore)
            .containsExactlyElementsOf(reversed.stream().map(EvidenceCandidate::fusedScore).toList());
        assertThat(forward.getFirst().chunkId()).isEqualTo("shared");
        assertThat(forward.getFirst().contributions()).hasSize(2);
    }

    @Test
    void executedSubqueryNormalizationPreventsQueryCountWeightInflation() {
        Result dense = result(Branch.DENSE, List.of(
            candidate(Branch.DENSE, "dense", "doc-1", "q-1", 1, 0.9),
            candidate(Branch.DENSE, "dense", "doc-1", "q-2", 1, 0.8)
        ));
        Result lexical = result(Branch.LEXICAL, List.of(
            candidate(Branch.LEXICAL, "lexical", "doc-2", "q-1", 1, 8.0)
        ));
        FusionOptions options = new FusionOptions(
            60,
            20,
            60,
            Map.of(Channel.DENSE, 2, Channel.LEXICAL, 1),
            Map.of()
        );

        List<EvidenceCandidate> fused = service.fuse(List.of(dense, lexical), null, options).candidates();

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).fusedScore()).isEqualTo(fused.get(1).fusedScore());
        assertThat(fused).extracting(EvidenceCandidate::chunkId).containsExactly("dense", "lexical");
    }

    @Test
    void rawScoresNeverAffectRrfOrdering() {
        Result first = result(Branch.DENSE, List.of(
            candidate(Branch.DENSE, "rank-one", "doc-1", "q", 1, -1000.0),
            candidate(Branch.DENSE, "rank-two", "doc-2", "q", 2, 1000.0)
        ));

        assertThat(service.fuse(List.of(first), null, FusionOptions.defaults()).candidates())
            .extracting(EvidenceCandidate::chunkId)
            .containsExactly("rank-one", "rank-two");
    }

    @Test
    void duplicateChunkWithinOneSubqueryKeepsOnlyItsBestRankContribution() {
        Result dense = result(Branch.DENSE, List.of(
            candidate(Branch.DENSE, "duplicate", "doc-1", "q", 3, 0.4),
            candidate(Branch.DENSE, "duplicate", "doc-1", "q", 1, 0.9)
        ));

        EvidenceCandidate fused = service.fuse(List.of(dense), null, FusionOptions.defaults()).candidates().getFirst();

        assertThat(fused.contributions()).singleElement()
            .satisfies(contribution -> assertThat(contribution.rawRank()).isEqualTo(1));
        assertThat(fused.fusedScore()).isEqualTo(1.0 / 61.0);
    }

    private Result result(Branch branch, List<Candidate> candidates) {
        return new Result(branch, candidates, new Diagnostics(Status.COMPLETED, 1, candidates.size(), null));
    }

    private Candidate candidate(
        Branch branch,
        String chunkId,
        String documentId,
        String subqueryId,
        int rank,
        double score
    ) {
        return new Candidate(
            branch,
            new SourceIdentity(chunkId, documentId, rank, rank * 10, rank * 10 + 5, rank, rank,
                "run-1", "revision-1", "section"),
            subqueryId,
            rank,
            score,
            "text " + chunkId
        );
    }
}
