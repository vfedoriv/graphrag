package io.github.vfedoriv.graphrag.search.retrieval.application;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts;

import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.ParentContext;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.SourceBounds;
import io.github.vfedoriv.graphrag.search.retrieval.ports.ParentContextRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.ParentContextRepository.AdjacentChunk;
import io.github.vfedoriv.graphrag.search.retrieval.ports.ParentContextRepository.ParentContextCandidate;
import io.github.vfedoriv.graphrag.search.retrieval.ports.ParentContextRepository.ParentContextRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchParentContextService {

    private final ParentContextRepository repository;

    public AdvancedSearchParentContextService(ParentContextRepository repository) {
        this.repository = repository;
    }

    public ExpansionResult expand(
        String knowledgeBaseId,
        List<EvidenceCandidate> rankedCandidates,
        ExpansionOptions options
    ) {
        ExpansionOptions effective = options == null ? ExpansionOptions.defaults() : options;
        List<EvidenceCandidate> candidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
        List<ParentContextCandidate> requests = new ArrayList<>();
        for (EvidenceCandidate candidate : candidates) {
            if (candidate.citationKind() != CitationKind.TEXT_CHILD || requests.size() >= effective.maxEvidence()) {
                continue;
            }
            requests.add(new ParentContextCandidate(
                candidate.fusedRank(),
                candidate.chunkId(),
                candidate.documentId(),
                candidate.source().processingRunId(),
                candidate.source().effectiveChunkerRevision()
            ));
        }
        if (requests.isEmpty()) {
            return new ExpansionResult(candidates, new ExpansionDiagnostics(0, 0, 0, Map.of()));
        }
        List<ParentContextRow> rows = new ArrayList<>(
            repository.load(knowledgeBaseId, requests, effective.adjacentChunks())
        );
        rows.sort(Comparator.comparingInt(ParentContextRow::rank));

        Map<String, ParentContext> contextByChild = new LinkedHashMap<>();
        Map<String, Integer> outcomes = new LinkedHashMap<>();
        Map<String, Integer> contextCountByDocument = new LinkedHashMap<>();
        Map<String, ParentContext> uniqueContexts = new LinkedHashMap<>();
        int tokenCount = 0;
        for (ParentContextRow row : rows) {
            ParentContext context = context(row, effective.includeText());
            if (context == null) {
                increment(outcomes, row.outcome());
                continue;
            }
            String contextKey = context.kind() + ":" + context.contextChunkId();
            ParentContext existing = uniqueContexts.get(contextKey);
            if (existing != null) {
                contextByChild.put(row.childId(), existing);
                increment(outcomes, "DEDUPLICATED");
                continue;
            }
            int documentCount = contextCountByDocument.getOrDefault(context.documentId(), 0);
            if (uniqueContexts.size() >= effective.maxContexts()) {
                increment(outcomes, "CONTEXT_BUDGET");
                continue;
            }
            if (documentCount >= effective.maxContextsPerDocument()) {
                increment(outcomes, "DOCUMENT_BUDGET");
                continue;
            }
            if (tokenCount + context.tokenEstimate() > effective.maxTokens()) {
                increment(outcomes, "TOKEN_BUDGET");
                continue;
            }
            uniqueContexts.put(contextKey, context);
            contextByChild.put(row.childId(), context);
            contextCountByDocument.put(context.documentId(), documentCount + 1);
            tokenCount += context.tokenEstimate();
            increment(outcomes, "INCLUDED_" + context.kind());
        }
        List<EvidenceCandidate> expanded = candidates.stream()
            .map(candidate -> candidate.withParentContext(contextByChild.get(candidate.chunkId())))
            .toList();
        return new ExpansionResult(
            expanded,
            new ExpansionDiagnostics(requests.size(), uniqueContexts.size(), tokenCount, Map.copyOf(outcomes))
        );
    }

    private ParentContext context(ParentContextRow row, boolean includeText) {
        if ("VALID_PARENT".equals(row.outcome())) {
            if (!hasText(row.parentId())) {
                return null;
            }
            return new ParentContext(
                row.parentId(),
                row.documentId(),
                "PARENT",
                includeText ? row.parentText() : null,
                new SourceBounds(
                    row.parentSourceStart(), row.parentSourceEnd(), row.parentPageStart(), row.parentPageEnd()
                ),
                row.strategyRevision(),
                List.of(row.parentId()),
                tokenEstimate(row.parentTokenEstimate(), row.parentText())
            );
        }
        if (!"NO_PARENT".equals(row.outcome()) && !"PARENT_NOT_FOUND".equals(row.outcome())) {
            return null;
        }
        List<AdjacentChunk> adjacency = row.adjacency();
        if (adjacency.isEmpty()) {
            return null;
        }
        String text = adjacency.stream()
            .map(AdjacentChunk::text)
            .filter(value -> value != null)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse(null);
        int tokens = adjacency.stream()
            .mapToInt(value -> tokenEstimate(value.tokenEstimate(), value.text()))
            .sum();
        return new ParentContext(
            row.childId(),
            row.documentId(),
            "ADJACENT",
            includeText ? text : null,
            new SourceBounds(
                adjacency.stream().map(AdjacentChunk::sourceStart).filter(value -> value != null)
                    .min(Integer::compareTo).orElse(null),
                adjacency.stream().map(AdjacentChunk::sourceEnd).filter(value -> value != null)
                    .max(Integer::compareTo).orElse(null),
                adjacency.stream().map(AdjacentChunk::pageStart).filter(value -> value != null)
                    .min(Integer::compareTo).orElse(null),
                adjacency.stream().map(AdjacentChunk::pageEnd).filter(value -> value != null)
                    .max(Integer::compareTo).orElse(null)
            ),
            row.strategyRevision(),
            adjacency.stream().map(AdjacentChunk::id).filter(value -> value != null).toList(),
            tokens
        );
    }

    private int tokenEstimate(Integer stored, String text) {
        if (stored != null && stored > 0) {
            return stored;
        }
        return Math.max(1, text == null ? 1 : (text.length() + 3) / 4);
    }

    private void increment(Map<String, Integer> outcomes, String outcome) {
        outcomes.merge(outcome == null ? "UNKNOWN" : outcome, 1, Integer::sum);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ExpansionOptions(
        int maxEvidence,
        int maxContexts,
        int maxContextsPerDocument,
        int maxTokens,
        int adjacentChunks,
        boolean includeText
    ) {
        public ExpansionOptions {
            if (maxEvidence < 1 || maxContexts < 1 || maxContextsPerDocument < 1 || maxTokens < 1
                || adjacentChunks < 0) {
                throw new IllegalArgumentException("parent-context bounds are invalid");
            }
        }

        public static ExpansionOptions defaults() {
            return new ExpansionOptions(20, 8, 2, 4096, 1, true);
        }
    }

    public record ExpansionDiagnostics(
        int evidenceConsidered,
        int contextCount,
        int tokenEstimate,
        Map<String, Integer> outcomes
    ) { }

    public record ExpansionResult(List<EvidenceCandidate> candidates, ExpansionDiagnostics diagnostics) { }
}
