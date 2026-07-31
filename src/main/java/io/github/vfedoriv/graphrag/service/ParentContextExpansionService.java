package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.dto.HybridSearchCitationKind;
import io.github.vfedoriv.graphrag.dto.HybridSearchExpandedContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchExpansionDiagnostics;
import io.github.vfedoriv.graphrag.dto.HybridSearchHit;
import io.github.vfedoriv.graphrag.dto.HybridSearchRetrievalEvidence;
import io.github.vfedoriv.graphrag.dto.HybridSearchSourceRange;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.repository.ParentContextRepository;
import io.github.vfedoriv.graphrag.repository.ParentContextRepository.AdjacentChunk;
import io.github.vfedoriv.graphrag.repository.ParentContextRepository.ParentContextCandidate;
import io.github.vfedoriv.graphrag.repository.ParentContextRepository.ParentContextRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Service
@Slf4j
public class ParentContextExpansionService {

    private final ParentContextRepository parentContextRepository;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public ParentContextExpansionService(ParentContextRepository parentContextRepository) {
        this(parentContextRepository, Metrics.globalRegistry, ObservationRegistry.NOOP);
    }

    @Autowired
    public ParentContextExpansionService(
        ParentContextRepository parentContextRepository,
        MeterRegistry meterRegistry,
        ObservationRegistry observationRegistry
    ) {
        this.parentContextRepository = parentContextRepository;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public ExpansionResult expand(
        String knowledgeBaseId,
        List<HybridSearchHit> rankedHits,
        RuntimeSettingsService.QuerySettings settings,
        boolean includeContextText
    ) {
        long startNanos = System.nanoTime();
        Observation observation = Observation.start("graphrag.search.parent-context-expansion", observationRegistry);
        observation.lowCardinalityKeyValue(
            "search.parent_context.enabled",
            String.valueOf(settings.parentContextExpansionEnabled())
        );
        String strategyRevisions = rankedHits.stream()
            .map(HybridSearchHit::retrievalEvidence)
            .map(HybridSearchRetrievalEvidence::strategyRevision)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .limit(8)
            .collect(Collectors.joining(","));
        observation.highCardinalityKeyValue("search.parent_context.strategy_revisions", strategyRevisions);
        if (!settings.parentContextExpansionEnabled() || rankedHits.isEmpty()) {
            ExpansionResult result = new ExpansionResult(
                rankedHits,
                List.of(),
                new HybridSearchExpansionDiagnostics(
                    settings.parentContextExpansionEnabled(),
                    0,
                    0,
                    0,
                    Map.of(settings.parentContextExpansionEnabled() ? "NO_EVIDENCE" : "DISABLED", 1),
                    LogMetadata.elapsedMillis(startNanos)
                )
            );
            recordDiagnostics(result.diagnostics(), startNanos, observation);
            return result;
        }

        try {
            List<ParentContextCandidate> candidates = new ArrayList<>();
            for (int rank = 0; rank < rankedHits.size(); rank++) {
                HybridSearchHit hit = rankedHits.get(rank);
                HybridSearchRetrievalEvidence evidence = hit.retrievalEvidence();
                candidates.add(new ParentContextCandidate(
                    rank,
                    hit.chunkId(),
                    hit.documentId(),
                    evidence.processingRunId(),
                    evidence.strategyRevision()
                ));
            }

            List<ParentContextRow> rows = new ArrayList<>(parentContextRepository.load(
                knowledgeBaseId,
                candidates,
                settings.parentContextAdjacentChunks()
            ));
            rows.sort(Comparator.comparingInt(ParentContextRow::rank));
            ExpansionResult result = plan(rankedHits, rows, settings, includeContextText, startNanos);
            log.info(
                "Parent context expansion completed: knowledgeBaseId={}, evidenceCount={}, contextCount={}, "
                    + "contextTokenEstimate={}, outcomes={}, executionTimeMs={}",
                knowledgeBaseId,
                result.diagnostics().evidenceConsidered(),
                result.diagnostics().contextCount(),
                result.diagnostics().contextTokenEstimate(),
                result.diagnostics().outcomes(),
                result.diagnostics().executionTimeMs()
            );
            recordDiagnostics(result.diagnostics(), startNanos, observation);
            return result;
        } catch (RuntimeException exception) {
            observation.error(exception);
            observation.stop();
            throw exception;
        }
    }

    ExpansionResult plan(
        List<HybridSearchHit> rankedHits,
        List<ParentContextRow> rows,
        RuntimeSettingsService.QuerySettings settings,
        boolean includeContextText,
        long startNanos
    ) {
        Map<String, Integer> outcomes = new LinkedHashMap<>();
        Map<String, HybridSearchExpandedContext> contexts = new LinkedHashMap<>();
        Map<String, Integer> contextCountByDocument = new LinkedHashMap<>();
        Map<String, String> contextByEvidence = new LinkedHashMap<>();
        int evidenceConsidered = 0;
        int contextTokens = 0;

        for (ParentContextRow row : rows) {
            String childId = row.childId();
            if (childId == null) {
                increment(outcomes, row.outcome());
                continue;
            }
            if (evidenceConsidered >= settings.parentContextMaxEvidence()) {
                increment(outcomes, "EVIDENCE_BUDGET");
                continue;
            }
            evidenceConsidered++;
            String outcome = row.outcome();
            ContextCandidate candidate = contextCandidate(row, outcome);
            if (candidate == null) {
                increment(outcomes, outcome);
                log.debug(
                    "Parent context candidate rejected: childFingerprint={}, outcome={}, strategyRevision={}",
                    LogMetadata.fingerprint(childId),
                    outcome,
                    row.strategyRevision()
                );
                continue;
            }

            String key = candidate.kind() + ":" + candidate.contextChunkId();
            HybridSearchExpandedContext existing = contexts.get(key);
            if (existing != null) {
                List<String> evidenceIds = new ArrayList<>(existing.evidenceChunkIds());
                if (!evidenceIds.contains(childId)) {
                    evidenceIds.add(childId);
                }
                contexts.put(key, copyWithEvidence(existing, evidenceIds));
                contextByEvidence.put(childId, existing.contextChunkId());
                increment(outcomes, "DEDUPLICATED");
                continue;
            }

            int documentContexts = contextCountByDocument.getOrDefault(candidate.documentId(), 0);
            if (contexts.size() >= settings.parentContextMaxParents()) {
                increment(outcomes, "PARENT_BUDGET");
                continue;
            }
            if (documentContexts >= settings.parentContextMaxPerDocument()) {
                increment(outcomes, "DOCUMENT_BUDGET");
                continue;
            }
            if (contextTokens + candidate.tokenEstimate() > settings.parentContextMaxTokens()) {
                increment(outcomes, "TOKEN_BUDGET");
                continue;
            }

            HybridSearchExpandedContext context = new HybridSearchExpandedContext(
                candidate.contextChunkId(),
                candidate.documentId(),
                candidate.kind(),
                includeContextText ? candidate.text() : null,
                candidate.range(),
                candidate.strategyRevision(),
                List.of(childId),
                candidate.contributingChunkIds(),
                candidate.tokenEstimate(),
                HybridSearchCitationKind.CONTEXT_ONLY
            );
            contexts.put(key, context);
            contextByEvidence.put(childId, context.contextChunkId());
            contextCountByDocument.put(candidate.documentId(), documentContexts + 1);
            contextTokens += candidate.tokenEstimate();
            increment(outcomes, "INCLUDED_" + candidate.kind());
        }

        List<HybridSearchHit> hits = rankedHits.stream()
            .map(hit -> hit.withExpandedContextId(contextByEvidence.get(hit.chunkId())))
            .toList();
        HybridSearchExpansionDiagnostics diagnostics = new HybridSearchExpansionDiagnostics(
            true,
            evidenceConsidered,
            contexts.size(),
            contextTokens,
            outcomes,
            LogMetadata.elapsedMillis(startNanos)
        );
        return new ExpansionResult(hits, List.copyOf(contexts.values()), diagnostics);
    }

    private void recordDiagnostics(
        HybridSearchExpansionDiagnostics diagnostics,
        long startNanos,
        Observation observation
    ) {
        observation.highCardinalityKeyValue(
            "search.parent_context.evidence_count",
            String.valueOf(diagnostics.evidenceConsidered())
        );
        observation.highCardinalityKeyValue(
            "search.parent_context.context_count",
            String.valueOf(diagnostics.contextCount())
        );
        observation.highCardinalityKeyValue(
            "search.parent_context.token_estimate",
            String.valueOf(diagnostics.contextTokenEstimate())
        );
        for (Map.Entry<String, Integer> outcome : diagnostics.outcomes().entrySet()) {
            observation.highCardinalityKeyValue(
                "search.parent_context.outcome." + outcome.getKey().toLowerCase(java.util.Locale.ROOT),
                String.valueOf(outcome.getValue())
            );
            Counter.builder("graphrag.search.parent_context.outcomes")
                .description("Parent-context expansion validation and budget outcomes")
                .tag("outcome", outcome.getKey())
                .register(meterRegistry)
                .increment(outcome.getValue());
        }
        DistributionSummary.builder("graphrag.search.parent_context.contexts")
            .description("Expanded contexts included per search")
            .register(meterRegistry)
            .record(diagnostics.contextCount());
        DistributionSummary.builder("graphrag.search.parent_context.tokens")
            .description("Estimated expanded-context tokens per search")
            .register(meterRegistry)
            .record(diagnostics.contextTokenEstimate());
        Timer.builder("graphrag.search.parent_context.latency")
            .description("Parent-context expansion latency")
            .register(meterRegistry)
            .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        observation.stop();
    }

    private ContextCandidate contextCandidate(ParentContextRow row, String outcome) {
        if ("VALID_PARENT".equals(outcome)) {
            String parentId = row.parentId();
            String text = row.parentText();
            return new ContextCandidate(
                parentId,
                row.documentId(),
                "PARENT",
                text,
                new HybridSearchSourceRange(
                    row.parentSourceStart(),
                    row.parentSourceEnd(),
                    row.parentPageStart(),
                    row.parentPageEnd()
                ),
                row.strategyRevision(),
                List.of(parentId),
                positiveTokenEstimate(row.parentTokenEstimate(), text)
            );
        }
        if (!"NO_PARENT".equals(outcome) && !"PARENT_NOT_FOUND".equals(outcome)) {
            return null;
        }
        List<AdjacentChunk> adjacency = row.adjacency();
        if (adjacency.isEmpty()) {
            return null;
        }
        List<String> ids = adjacency.stream().map(AdjacentChunk::id).filter(Objects::nonNull).toList();
        String text = adjacency.stream()
            .map(AdjacentChunk::text)
            .filter(Objects::nonNull)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse(null);
        int tokens = adjacency.stream()
            .mapToInt(value -> positiveTokenEstimate(value.tokenEstimate(), value.text()))
            .sum();
        return new ContextCandidate(
            row.childId(),
            row.documentId(),
            "ADJACENT",
            text,
            adjacencyRange(adjacency),
            row.strategyRevision(),
            ids,
            tokens
        );
    }

    private HybridSearchSourceRange adjacencyRange(List<AdjacentChunk> adjacency) {
        return new HybridSearchSourceRange(
            adjacency.stream().map(AdjacentChunk::sourceStart).filter(Objects::nonNull)
                .min(Integer::compareTo).orElse(null),
            adjacency.stream().map(AdjacentChunk::sourceEnd).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(null),
            adjacency.stream().map(AdjacentChunk::pageStart).filter(Objects::nonNull)
                .min(Integer::compareTo).orElse(null),
            adjacency.stream().map(AdjacentChunk::pageEnd).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(null)
        );
    }

    private HybridSearchExpandedContext copyWithEvidence(
        HybridSearchExpandedContext context,
        List<String> evidenceIds
    ) {
        return new HybridSearchExpandedContext(
            context.contextChunkId(),
            context.documentId(),
            context.kind(),
            context.contextText(),
            context.contextRange(),
            context.strategyRevision(),
            evidenceIds,
            context.contributingChunkIds(),
            context.tokenEstimate(),
            context.citationKind()
        );
    }

    private void increment(Map<String, Integer> outcomes, String outcome) {
        String safeOutcome = outcome == null ? "UNKNOWN" : outcome;
        outcomes.merge(safeOutcome, 1, Integer::sum);
    }

    private int positiveTokenEstimate(Integer value, String text) {
        int estimate = value == null ? 0 : value;
        return estimate > 0 ? estimate : Math.max(1, text == null ? 1 : (text.length() + 3) / 4);
    }

    public record ExpansionResult(
        List<HybridSearchHit> hits,
        List<HybridSearchExpandedContext> contexts,
        HybridSearchExpansionDiagnostics diagnostics
    ) {
    }

    private record ContextCandidate(
        String contextChunkId,
        String documentId,
        String kind,
        String text,
        HybridSearchSourceRange range,
        String strategyRevision,
        List<String> contributingChunkIds,
        int tokenEstimate
    ) {
    }
}
