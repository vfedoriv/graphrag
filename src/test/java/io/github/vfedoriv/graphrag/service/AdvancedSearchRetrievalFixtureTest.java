package io.github.vfedoriv.graphrag.service;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchFusionService;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchFusionService.FusionOptions;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvancedSearchRetrievalFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdvancedSearchFusionService fusionService = new AdvancedSearchFusionService();

    @Test
    void versionOneFixtureMeetsMixedRecallAndSemanticRegressionGuard() throws Exception {
        RetrievalFixture fixture;
        try (InputStream stream = getClass().getResourceAsStream(
            "/fixtures/retrieval/advanced-search-ranking-v1.json"
        )) {
            fixture = objectMapper.readValue(stream, RetrievalFixture.class);
        }

        assertThat(fixture.fixtureVersion()).isEqualTo("advanced-search-ranking-v1");
        FixtureCase mixed = fixture.cases().stream().filter(value -> "MIXED".equals(value.kind())).findFirst().orElseThrow();
        FixtureCase semantic = fixture.cases().stream()
            .filter(value -> "SEMANTIC_ONLY".equals(value.kind())).findFirst().orElseThrow();

        double mixedRecall = recallAt10(fuse(mixed), mixed.relevantChunkIds());
        double semanticFusedRecall = recallAt10(fuse(semantic), semantic.relevantChunkIds());
        double semanticBaselineRecall = recallAt10(
            semantic.candidates().stream()
                .filter(value -> value.channel() == Branch.DENSE)
                .sorted(java.util.Comparator.comparingInt(FixtureCandidate::rank))
                .map(FixtureCandidate::chunkId)
                .toList(),
            semantic.relevantChunkIds()
        );

        assertThat(mixedRecall).isGreaterThanOrEqualTo(fixture.mixedMinimumRecallAt10());
        assertThat(semanticBaselineRecall - semanticFusedRecall)
            .isLessThanOrEqualTo(fixture.semanticMaximumRecallRegression());
    }

    private List<String> fuse(FixtureCase fixtureCase) {
        Map<Branch, List<Candidate>> byBranch = new EnumMap<>(Branch.class);
        for (FixtureCandidate candidate : fixtureCase.candidates()) {
            byBranch.computeIfAbsent(candidate.channel(), ignored -> new ArrayList<>()).add(toCandidate(candidate));
        }
        List<Result> results = byBranch.entrySet().stream()
            .map(entry -> new Result(
                entry.getKey(),
                entry.getValue(),
                new Diagnostics(Status.COMPLETED, 0, entry.getValue().size(), null)
            ))
            .toList();
        return fusionService.fuse(results, null, FusionOptions.defaults()).candidates().stream()
            .map(EvidenceCandidate::chunkId)
            .toList();
    }

    private Candidate toCandidate(FixtureCandidate candidate) {
        return new Candidate(
            candidate.channel(),
            new SourceIdentity(
                candidate.chunkId(), candidate.documentId(), candidate.rank(), 0, 10, 1, 1,
                "fixture-run", "fixture-revision", "fixture"
            ),
            candidate.subqueryId(),
            candidate.rank(),
            candidate.score(),
            "fixture text"
        );
    }

    private double recallAt10(List<String> rankedIds, List<String> relevantIds) {
        Set<String> top = new LinkedHashSet<>(rankedIds.stream().limit(10).toList());
        long found = relevantIds.stream().filter(top::contains).count();
        return relevantIds.isEmpty() ? 1.0 : (double) found / relevantIds.size();
    }

    record RetrievalFixture(
        String fixtureVersion,
        double mixedMinimumRecallAt10,
        double semanticMaximumRecallRegression,
        List<FixtureCase> cases
    ) { }

    record FixtureCase(
        String id,
        String kind,
        List<String> relevantChunkIds,
        List<FixtureCandidate> candidates
    ) { }

    record FixtureCandidate(
        Branch channel,
        String chunkId,
        String documentId,
        String subqueryId,
        int rank,
        double score
    ) { }
}
