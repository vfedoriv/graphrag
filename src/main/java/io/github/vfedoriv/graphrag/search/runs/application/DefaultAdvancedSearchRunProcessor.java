package io.github.vfedoriv.graphrag.search.runs.application;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchPlanningContracts;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchParentContextService;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchPlanValidator;
import io.github.vfedoriv.graphrag.search.ranking.application.AdvancedSearchRankingPipeline;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchFusionService;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchFollowUpPolicy;
import io.github.vfedoriv.graphrag.search.answering.adapters.model.AdvancedSearchSufficiencyEvaluator;

import io.github.vfedoriv.graphrag.search.answering.application.AdvancedSearchCitationMetadataService;
import io.github.vfedoriv.graphrag.search.retrieval.adapters.model.AdvancedSearchPlanner;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchGraphRetriever;
import io.github.vfedoriv.graphrag.search.retrieval.application.DenseTextRetriever;
import io.github.vfedoriv.graphrag.search.retrieval.application.DocumentMetadataTextRetriever;
import io.github.vfedoriv.graphrag.search.retrieval.application.LexicalTextRetriever;
import io.github.vfedoriv.graphrag.search.runs.adapters.codec.AdvancedSearchResultCodec;
import io.github.vfedoriv.graphrag.search.runs.ports.AdvancedSearchRunProcessor;
import io.github.vfedoriv.graphrag.service.AiProfileContext;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Row;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchPlanningContracts.Refinement;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts;
import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchFollowUpPolicy.Decision;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchFusionService.FusionOptions;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchParentContextService.ExpansionOptions;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchPlanValidator.ValidatedPlan;
import io.github.vfedoriv.graphrag.search.ranking.application.AdvancedSearchRankingPipeline.RankingRequest;
import io.github.vfedoriv.graphrag.search.ranking.application.AdvancedSearchRankingPipeline.RankingResult;
import io.github.vfedoriv.graphrag.search.answering.adapters.model.AdvancedSearchSufficiencyEvaluator.Outcome;
import io.github.vfedoriv.graphrag.search.answering.adapters.model.AdvancedSearchAnswerSynthesizer;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchCitationCatalog;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchCitationCatalog.Catalog;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.observability.AdvancedSearchMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class DefaultAdvancedSearchRunProcessor implements AdvancedSearchRunProcessor {
    private final DenseTextRetriever dense;
    private final LexicalTextRetriever lexical;
    private final DocumentMetadataTextRetriever metadata;
    private final AdvancedSearchGraphRetriever graphRetriever;
    private final AdvancedSearchPlanner planner;
    private final AdvancedSearchSufficiencyEvaluator sufficiencyEvaluator;
    private final AdvancedSearchFollowUpPolicy followUpPolicy;
    private final AdvancedSearchRankingPipeline ranking;
    private final SearchSchemas schemaParser;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor branchExecutor;
    private final AdvancedSearchCitationCatalog citationCatalog;
    private final AdvancedSearchCitationMetadataService citationMetadataService;
    private final AdvancedSearchAnswerSynthesizer answerSynthesizer;
    private final AiObservationService observations;
    private final AdvancedSearchMetrics metrics;

    public DefaultAdvancedSearchRunProcessor(
        DenseTextRetriever dense,
        LexicalTextRetriever lexical,
        DocumentMetadataTextRetriever metadata,
        AdvancedSearchGraphRetriever graphRetriever,
        AdvancedSearchPlanner planner,
        AdvancedSearchSufficiencyEvaluator sufficiencyEvaluator,
        AdvancedSearchFollowUpPolicy followUpPolicy,
        AdvancedSearchRankingPipeline ranking,
        SearchSchemas schemaParser,
        ObjectMapper objectMapper,
        AdvancedSearchCitationCatalog citationCatalog,
        AdvancedSearchCitationMetadataService citationMetadataService,
        AdvancedSearchAnswerSynthesizer answerSynthesizer,
        AiObservationService observations,
        AdvancedSearchMetrics metrics,
        @Qualifier("advancedSearchBranchExecutor") ThreadPoolTaskExecutor branchExecutor
    ) {
        this.dense = dense;
        this.lexical = lexical;
        this.metadata = metadata;
        this.graphRetriever = graphRetriever;
        this.planner = planner;
        this.sufficiencyEvaluator = sufficiencyEvaluator;
        this.followUpPolicy = followUpPolicy;
        this.ranking = ranking;
        this.schemaParser = schemaParser;
        this.objectMapper = objectMapper;
        this.citationCatalog = citationCatalog;
        this.citationMetadataService = citationMetadataService;
        this.answerSynthesizer = answerSynthesizer;
        this.observations = observations;
        this.metrics = metrics;
        this.branchExecutor = branchExecutor;
    }

    @Override
    public ProcessingResult process(Context context) {
        if (context.activeAiProfileId() == null || context.activeAiProfileId().isBlank()) {
            return processProfileScoped(context);
        }
        return AiProfileContext.withProfile(context.activeAiProfileId(), () -> processProfileScoped(context));
    }

    private ProcessingResult processProfileScoped(Context context) {
        try (AiObservationScope workflow = observations.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_ADVANCED_SEARCH,
            null,
            Map.of("advanced_search.knowledge_base_id", context.knowledgeBaseId())
        ))) {
            try {
                ProcessingResult result = processPipeline(context, workflow);
                workflow.success();
                return result;
            } catch (RuntimeException exception) {
                workflow.error(exception);
                throw exception;
            }
        }
    }

    private ProcessingResult processPipeline(Context context, AiObservationScope workflow) {
        requireContinue(context);
        SchemaSnapshot schemaContext = schemaContext(context);
        ValidatedPlan plan = observe("advanced-search.planning", () ->
            planner.plan(context.query(), schemaContext, context.settings(), context.deadline()));
        context.stageChanged().accept(AdvancedSearchRunStage.RETRIEVAL);

        List<Result> textResults = new ArrayList<>();
        List<Attempt> attempts = new ArrayList<>();
        int totalBranches = 3 + plan.graphPlans().size();
        List<Result> initialResults = executeTextRound(
            plan.subqueries(), plan.metadata(), context, 1, attempts
        );
        textResults.addAll(initialResults);
        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result graphResult =
            executeGraphRound(plan.graphPlans(), schemaContext, context, attempts);

        requireContinue(context);
        context.stageChanged().accept(AdvancedSearchRunStage.RANKING);
        RankingResult ranked = rank(context, textResults, graphResult);
        requireContinue(context);

        RankingResult initialRanking = ranked;
        Outcome sufficiency = observe("advanced-search.evaluation", () -> sufficiencyEvaluator.evaluate(
            context.query(), plan, initialRanking.candidates(), context.settings(), context.deadline()));
        Decision followUp = observe("advanced-search.follow-up", () -> followUpPolicy.decide(
            sufficiency.result(), context.deadline(), context.settings(), context.cancelled()));
        if (followUp.execute()) {
            totalBranches += 3;
            requireContinue(context);
            context.stageChanged().accept(AdvancedSearchRunStage.RETRIEVAL);
            List<Subquery> refined = followUp.refinements().stream().map(this::subquery).toList();
            textResults.addAll(executeTextRound(refined, plan.metadata(), context, 2, attempts));
            requireContinue(context);
            context.stageChanged().accept(AdvancedSearchRunStage.RANKING);
            ranked = rank(context, textResults, graphResult);
        }

        requireContinue(context);
        context.stageChanged().accept(AdvancedSearchRunStage.SYNTHESIS);
        Catalog catalog = citationMetadataService.enrich(
            context.knowledgeBaseId(), citationCatalog.build(ranked.evidence(), true));
        io.github.vfedoriv.graphrag.search.answering.domain.AnswerSynthesis.Outcome answer = answerSynthesizer.synthesize(
            context.query(), catalog, context.deadline());
        metrics.retrieval(attempts);
        metrics.answer(answer, followUp.execute(), ranked.candidates().size());
        ObjectNode payload = payload(context, plan, sufficiency, followUp, ranked, attempts, catalog, answer);
        int successful = (int) attempts.stream().filter(value -> "COMPLETED".equals(value.status())).count();
        workflow.highCardinalityAttributes(Map.of(
            "advanced_search.retrieval.attempts", String.valueOf(attempts.size()),
            "advanced_search.evidence.count", String.valueOf(ranked.candidates().size()),
            "advanced_search.citation.count", String.valueOf(catalog.evidence().size()),
            "advanced_search.claim.count", String.valueOf(answer.answer().claims().size()),
            "advanced_search.repair.used", String.valueOf(answer.diagnostics().repairAttempted()),
            "advanced_search.abstained", String.valueOf(!answer.answered())
        ));
        return new ProcessingResult(
            payload, ranked.candidates().size(), attempts, successful, totalBranches,
            answer.answered(), answer.answered() ? null : answer.diagnostics().outcomeCategory());
    }

    private List<Result> executeTextRound(
        List<Subquery> subqueries,
        MetadataConstraints constraints,
        Context context,
        int round,
        List<Attempt> attempts
    ) {
        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Request request =
            new io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Request(
                context.knowledgeBaseId(), subqueries, constraints, context.settings().candidateLimit(),
                context.includeEvidenceText(), context.deadline()
            );
        List<Callable<Result>> tasks = List.of(
            () -> observe("advanced-search.retriever.dense", () -> dense.retrieve(request)),
            () -> observe("advanced-search.retriever.lexical", () -> lexical.retrieve(request)),
            () -> observe("advanced-search.retriever.metadata", () -> metadata.retrieve(request))
        );
        List<Result> results = executeBranches(tasks, context);
        results.forEach(value -> attempts.add(textAttempt(round, value)));
        return results;
    }

    private io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result executeGraphRound(
        List<GraphPlan> graphPlans,
        SchemaSnapshot schemaContext,
        Context context,
        List<Attempt> attempts
    ) {
        if (graphPlans.isEmpty() || schemaContext == null) {
            return null;
        }
        List<Row> rows = new ArrayList<>();
        List<Diagnostics> diagnostics = new ArrayList<>();
        for (int index = 0; index < graphPlans.size(); index++) {
            requireContinue(context);
            GraphPlan plan = graphPlans.get(index);
            Duration remaining = remaining(context.deadline());
            io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result result =
                observe("advanced-search.retriever.graph", () -> graphRetriever.retrieve(
                    new Request(context.knowledgeBaseId(), plan, context.deadline()),
                    schemaContext,
                    context.settings().candidateLimit(),
                    remaining
                ));
            rows.addAll(result.rows());
            diagnostics.add(result.diagnostics());
            attempts.add(graphAttempt("graph-" + (index + 1), result.diagnostics()));
        }
        Status status = diagnostics.stream().anyMatch(value -> value.status() == Status.COMPLETED)
            ? Status.COMPLETED
            : diagnostics.stream().anyMatch(value -> value.status() == Status.DEADLINE_EXCEEDED)
                ? Status.DEADLINE_EXCEEDED : Status.FAILED;
        long latency = diagnostics.stream().mapToLong(Diagnostics::latencyMs).sum();
        String category = diagnostics.stream().map(Diagnostics::failureCategory)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        return new io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result(
            List.copyOf(rows), new Diagnostics(status, latency, rows.size(), category)
        );
    }

    private RankingResult rank(
        Context context,
        List<Result> textResults,
        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result graphResult
    ) {
        return observe("advanced-search.ranking", () -> ranking.rank(new RankingRequest(
            context.knowledgeBaseId(), context.query(), textResults, graphResult,
            new FusionOptions(
                Math.min(AdvancedSearchRankingContracts.MAX_FUSION_POOL, context.settings().candidateLimit()),
                context.settings().graphExpansionFactLimit(), context.settings().candidateLimit(), Map.of(), Map.of()),
            context.settings().graphExpansionSeedLimit(), context.settings().graphExpansionFactLimit(),
            new ExpansionOptions(
                context.maximumEvidence(), context.maximumEvidence(),
                AdvancedSearchRankingContracts.DEFAULT_PER_DOCUMENT_CAP, 4096, 1, context.includeEvidenceText()),
            context.settings().rerankPoolSize(), context.maximumEvidence(),
            AdvancedSearchRankingContracts.DEFAULT_PER_DOCUMENT_CAP, false
        )));
    }

    private ObjectNode payload(
        Context context,
        ValidatedPlan plan,
        Outcome sufficiency,
        Decision followUp,
        RankingResult result,
        List<Attempt> attempts,
        Catalog catalog,
        io.github.vfedoriv.graphrag.search.answering.domain.AnswerSynthesis.Outcome answer
    ) {
        Catalog publicCatalog = context.includeEvidenceText() ? catalog : catalog.withoutText();
        ArrayNode evidence = objectMapper.valueToTree(publicCatalog.evidence());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("payloadVersion", AdvancedSearchResultCodec.PAYLOAD_VERSION);
        payload.set("answer", objectMapper.valueToTree(answer.answer()));
        payload.set("evidence", evidence);
        payload.set("contexts", objectMapper.valueToTree(publicCatalog.contexts()));
        payload.set("graphFacts", objectMapper.valueToTree(publicCatalog.graphFacts()));
        payload.set("answerDiagnostics", objectMapper.valueToTree(answer.diagnostics()));
        Map<String, Object> diagnostics = new java.util.LinkedHashMap<>();
        diagnostics.put("plan", plan.summary());
        diagnostics.put("sufficiency", sufficiency.summary());
        diagnostics.put("followUp", Map.of(
            "executed", followUp.execute(),
            "queryCount", followUp.refinements().size(),
            "skippedCategory", followUp.skippedCategory() == null ? "NONE" : followUp.skippedCategory()
        ));
        diagnostics.put("attempts", attempts);
        diagnostics.put("fusion", result.fusionDiagnostics());
        diagnostics.put("graphExpansion", result.graphExpansionDiagnostics());
        diagnostics.put("parentContext", result.parentContextDiagnostics());
        diagnostics.put("rerank", result.rerankDiagnostics());
        diagnostics.put("selection", result.selectionDiagnostics());
        diagnostics.put("sourceMetadata", Map.of("warnings", catalog.metadataWarnings()));
        payload.set("diagnostics", objectMapper.valueToTree(diagnostics));
        trimText(payload, new int[]{context.settings().maxEvidenceTextCharacters()});
        return payload;
    }

    private <T> T observe(String workflowName, Callable<T> operation) {
        try (AiObservationScope scope = observations.startWorkflow(
            new AiWorkflowContext(workflowName, null, Map.of()))) {
            try {
                T result = operation.call();
                scope.success();
                return result;
            } catch (RuntimeException exception) {
                scope.error(exception);
                throw exception;
            } catch (Exception exception) {
                scope.error(exception);
                throw new IllegalStateException(workflowName + " failed", exception);
            }
        }
    }

    private SchemaSnapshot schemaContext(Context context) {
        if (context.schemaSnapshotJson() == null || context.schemaSnapshotJson().isBlank()
            || context.schemaDefinitionId() == null || context.schemaDefinitionId().isBlank()) {
            return null;
        }
        return schemaParser.parseCaptured(context.knowledgeBaseId(), context.schemaDefinitionId(),
            context.schemaContentHash(), context.schemaSnapshotJson());
    }

    private Subquery subquery(Refinement refinement) {
        return new Subquery(refinement.id(), refinement.query());
    }

    private Attempt textAttempt(int round, Result result) {
        return new Attempt(
            round,
            null,
            result.branch().name(),
            result.diagnostics().status().name(),
            result.diagnostics().candidateCount(),
            result.diagnostics().latencyMs(),
            result.diagnostics().failureCategory()
        );
    }

    private Attempt graphAttempt(String id, Diagnostics diagnostics) {
        String status = diagnostics.status() == Status.VALIDATION_FAILED
            ? "FAILED" : diagnostics.status().name();
        return new Attempt(
            1,
            id,
            "GRAPH",
            status,
            diagnostics.rowCount(),
            diagnostics.latencyMs(),
            diagnostics.failureCategory()
        );
    }

    private List<Result> executeBranches(List<Callable<Result>> tasks, Context context) {
        Duration remaining = remaining(context.deadline());
        if (remaining.isZero()) {
            throw new AdvancedSearchStoppedException("DEADLINE_EXCEEDED");
        }
        try {
            List<Future<Result>> futures = branchExecutor.getThreadPoolExecutor().invokeAll(
                tasks, remaining.toMillis(), TimeUnit.MILLISECONDS);
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                if (!future.isCancelled()) {
                    results.add(future.get());
                }
            }
            return List.copyOf(results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AdvancedSearchStoppedException("CANCELLED");
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Advanced-search retrieval branch failed", exception.getCause());
        }
    }

    private Duration remaining(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private void requireContinue(Context context) {
        if (context.cancelled().getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new AdvancedSearchStoppedException("CANCELLED");
        }
        if (!Instant.now().isBefore(context.deadline())) {
            throw new AdvancedSearchStoppedException("DEADLINE_EXCEEDED");
        }
    }

    private void trimText(JsonNode node, int[] remaining) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("text".equals(entry.getKey()) && entry.getValue().isTextual()) {
                    String value = entry.getValue().asText();
                    int allowed = Math.max(0, Math.min(remaining[0], value.length()));
                    ((ObjectNode) node).put(entry.getKey(), value.substring(0, allowed));
                    remaining[0] -= allowed;
                } else {
                    trimText(entry.getValue(), remaining);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> trimText(child, remaining));
        }
    }

    static final class AdvancedSearchStoppedException extends RuntimeException {
        AdvancedSearchStoppedException(String category) { super(category); }
    }
}
