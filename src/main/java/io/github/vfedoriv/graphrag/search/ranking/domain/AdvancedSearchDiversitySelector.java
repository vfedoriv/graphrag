package io.github.vfedoriv.graphrag.search.ranking.domain;

import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchDiversitySelector {

    public SelectionResult select(
        List<EvidenceCandidate> rankedCandidates,
        int requestedMaximum,
        int perDocumentCap,
        boolean comparisonPolicy
    ) {
        if (requestedMaximum < 1
            || requestedMaximum > io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.MAX_FINAL_EVIDENCE) {
            throw new IllegalArgumentException("requestedMaximum must be between 1 and 20");
        }
        if (perDocumentCap < 1) {
            throw new IllegalArgumentException("perDocumentCap must be positive");
        }
        List<EvidenceCandidate> candidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
        Map<String, Integer> selectedByDocument = new LinkedHashMap<>();
        List<EvidenceCandidate> selected = new ArrayList<>();
        int skippedForDiversity = 0;
        int effectiveCap = comparisonPolicy ? requestedMaximum : perDocumentCap;
        for (EvidenceCandidate candidate : candidates) {
            if (selected.size() >= requestedMaximum) {
                break;
            }
            int count = selectedByDocument.getOrDefault(candidate.documentId(), 0);
            if (count >= effectiveCap) {
                skippedForDiversity++;
                continue;
            }
            selected.add(candidate);
            selectedByDocument.put(candidate.documentId(), count + 1);
        }
        return new SelectionResult(
            List.copyOf(selected),
            new SelectionDiagnostics(
                requestedMaximum,
                effectiveCap,
                comparisonPolicy,
                skippedForDiversity,
                Map.copyOf(selectedByDocument)
            )
        );
    }

    public record SelectionDiagnostics(
        int requestedMaximum,
        int effectivePerDocumentCap,
        boolean comparisonPolicy,
        int skippedForDiversity,
        Map<String, Integer> selectedByDocument
    ) { }

    public record SelectionResult(List<EvidenceCandidate> candidates, SelectionDiagnostics diagnostics) { }
}
