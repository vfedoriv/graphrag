package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.service.AdvancedSearchFusionService.FusionOptions;
import io.github.vfedoriv.graphrag.service.AdvancedSearchParentContextService.ExpansionOptions;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRankingPipeline.RankingRequest;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRankingPipeline.RankingResult;
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
    private final AdvancedSearchRankingPipeline ranking;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor branchExecutor;

    public DefaultAdvancedSearchRunProcessor(
        DenseTextRetriever dense,
        LexicalTextRetriever lexical,
        DocumentMetadataTextRetriever metadata,
        AdvancedSearchRankingPipeline ranking,
        ObjectMapper objectMapper,
        @Qualifier("advancedSearchBranchExecutor") ThreadPoolTaskExecutor branchExecutor
    ) {
        this.dense = dense; this.lexical = lexical; this.metadata = metadata; this.ranking = ranking;
        this.objectMapper = objectMapper; this.branchExecutor = branchExecutor;
    }

    @Override
    public ProcessingResult process(Context context) {
        requireContinue(context);
        Request request = new Request(
            context.knowledgeBaseId(), List.of(new Subquery("q1", context.query())),
            new MetadataConstraints(null, null), context.settings().candidateLimit(),
            context.includeEvidenceText(), context.deadline()
        );
        List<Callable<Result>> tasks = List.of(
            () -> dense.retrieve(request), () -> lexical.retrieve(request), () -> metadata.retrieve(request));
        List<Result> attempts = executeBranches(tasks, context);
        requireContinue(context);
        context.stageChanged().accept(AdvancedSearchRunStage.RANKING);
        RankingResult result = ranking.rank(new RankingRequest(
            context.knowledgeBaseId(), context.query(), attempts, null,
            new FusionOptions(
                Math.min(AdvancedSearchRankingContracts.MAX_FUSION_POOL, context.settings().candidateLimit()),
                context.settings().graphExpansionFactLimit(), context.settings().candidateLimit(), Map.of(), Map.of()),
            context.settings().graphExpansionSeedLimit(), context.settings().graphExpansionFactLimit(),
            new ExpansionOptions(
                context.maximumEvidence(), context.maximumEvidence(),
                AdvancedSearchRankingContracts.DEFAULT_PER_DOCUMENT_CAP, 4096, 1, context.includeEvidenceText()),
            context.settings().rerankPoolSize(), context.maximumEvidence(),
            AdvancedSearchRankingContracts.DEFAULT_PER_DOCUMENT_CAP, false
        ));
        requireContinue(context);
        ArrayNode evidence = objectMapper.valueToTree(result.candidates());
        trimText(evidence, new int[]{context.settings().maxEvidenceTextCharacters()});
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("payloadVersion", AdvancedSearchResultCodec.PAYLOAD_VERSION);
        payload.set("evidence", evidence);
        payload.set("diagnostics", objectMapper.valueToTree(Map.of(
            "branches", attempts.stream().map(Result::diagnostics).toList(),
            "fusion", result.fusionDiagnostics(), "rerank", result.rerankDiagnostics(),
            "selection", result.selectionDiagnostics()
        )));
        int successful = (int) attempts.stream().filter(value -> value.diagnostics().status() == Status.COMPLETED).count();
        return new ProcessingResult(payload, result.candidates().size(), attempts, successful);
    }

    private List<Result> executeBranches(List<Callable<Result>> tasks, Context context) {
        Duration remaining = Duration.between(Instant.now(), context.deadline());
        if (remaining.isNegative() || remaining.isZero()) {
            throw new AdvancedSearchStoppedException("DEADLINE_EXCEEDED");
        }
        try {
            List<Future<Result>> futures = branchExecutor.getThreadPoolExecutor().invokeAll(
                tasks, remaining.toMillis(), TimeUnit.MILLISECONDS);
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                if (future.isCancelled()) {
                    continue;
                }
                results.add(future.get());
            }
            return List.copyOf(results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AdvancedSearchStoppedException("CANCELLED");
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Advanced-search retrieval branch failed", exception.getCause());
        }
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
