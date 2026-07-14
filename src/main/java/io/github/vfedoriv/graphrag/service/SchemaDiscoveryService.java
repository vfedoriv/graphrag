package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.discovery.DiscoveryAggregator;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.FailureCategory;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.ResponseStatus;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.SourceStatus;
import io.github.vfedoriv.graphrag.discovery.DiscoverySourceAnalyzer;
import io.github.vfedoriv.graphrag.discovery.DiscoverySourcePreparer;
import io.github.vfedoriv.graphrag.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse.Reproducibility;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse.SourceOutcome;
import io.github.vfedoriv.graphrag.error.SchemaDiscoveryFailedException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class SchemaDiscoveryService {

    private final DiscoverySourcePreparer sourcePreparer;
    private final DiscoverySourceAnalyzer sourceAnalyzer;
    private final DiscoveryAggregator aggregator;
    private final RuntimeSettingsService runtimeSettingsService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiObservationService observationService;

    public SchemaDiscoveryService(
        DiscoverySourcePreparer sourcePreparer,
        DiscoverySourceAnalyzer sourceAnalyzer,
        DiscoveryAggregator aggregator,
        RuntimeSettingsService runtimeSettingsService,
        KnowledgeBaseService knowledgeBaseService,
        AiObservationService observationService
    ) {
        this.sourcePreparer = sourcePreparer;
        this.sourceAnalyzer = sourceAnalyzer;
        this.aggregator = aggregator;
        this.runtimeSettingsService = runtimeSettingsService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.observationService = observationService;
    }

    public SchemaDiscoveryResponse discover(
        String knowledgeBaseId, SchemaDiscoveryRequest request, List<MultipartFile> files
    ) {
        long startNanos = System.nanoTime();
        List<PreparedDiscoverySource> sources = sourcePreparer.prepare(knowledgeBaseId, request, files);
        RuntimeSettingsService.DiscoverySettings limits = runtimeSettingsService.discovery();
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        Map<String, String> workflowAttributes = Map.of(
            "ai.discovery.knowledge_base_id", knowledgeBaseId,
            "ai.discovery.source_count", Integer.toString(sources.size()),
            "ai.discovery.chunk_count", Integer.toString(sources.stream().mapToInt(source -> source.chunks().size()).sum()),
            "ai.discovery.limits_revision", DiscoveryContracts.LIMITS_REVISION
        );
        log.info("Schema discovery started: knowledgeBaseId={}, sourceCount={}, chunkCount={}, profileId={}, profileRevision={}",
            knowledgeBaseId, sources.size(), sources.stream().mapToInt(source -> source.chunks().size()).sum(),
            profile.getId(), profile.getRevision());
        try (AiObservationScope workflow = observationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_SCHEMA_DISCOVERY, null, workflowAttributes
        ))) {
            try {
                AnalysisBatch batch = analyze(sources, request, profile.getId(), limits);
                if (batch.successes().isEmpty()) {
                    SchemaDiscoveryFailedException exception = new SchemaDiscoveryFailedException(
                        "Schema discovery failed for all " + sources.size() + " sources"
                    );
                    workflow.error(exception);
                    throw exception;
                }
                DiscoveryAggregator.AggregateResult aggregate = aggregator.aggregate(batch.successes(), request);
                ResponseStatus status = batch.outcomes().stream().allMatch(outcome -> outcome.status() == SourceStatus.SUCCEEDED)
                    ? ResponseStatus.COMPLETED : ResponseStatus.PARTIAL;
                workflow.highCardinalityAttribute("ai.discovery.status", status.name().toLowerCase(Locale.ROOT));
                workflow.highCardinalityAttribute("ai.discovery.candidate_count", Integer.toString(aggregate.candidates().size()));
                workflow.highCardinalityAttribute("ai.discovery.conflict_count", Integer.toString(aggregate.conflicts().size()));
                workflow.success();
                log.info("Schema discovery completed: knowledgeBaseId={}, status={}, succeededSources={}, failedSources={}, candidateCount={}, conflictCount={}, warningCount={}, elapsedMs={}",
                    knowledgeBaseId, status, batch.successes().size(), sources.size() - batch.successes().size(),
                    aggregate.candidates().size(), aggregate.conflicts().size(), aggregate.warnings().size(),
                    LogMetadata.elapsedMillis(startNanos));
                return new SchemaDiscoveryResponse(status, aggregate.candidates(), aggregate.conflicts(), aggregate.warnings(),
                    batch.outcomes(), aggregate.schema(), new Reproducibility(profile.getId(), profile.getRevision(),
                        DiscoveryContracts.PROMPT_CONTRACT_REVISION, DiscoveryContracts.CANDIDATE_CONTRACT_REVISION,
                        DiscoveryContracts.LIMITS_REVISION));
            } catch (RuntimeException exception) {
                workflow.error(exception);
                log.error("Schema discovery failed: knowledgeBaseId={}, sourceCount={}, exceptionType={}, elapsedMs={}",
                    knowledgeBaseId, sources.size(), LogMetadata.exceptionType(exception), LogMetadata.elapsedMillis(startNanos));
                throw exception;
            }
        }
    }

    private AnalysisBatch analyze(
        List<PreparedDiscoverySource> sources,
        SchemaDiscoveryRequest request,
        String profileId,
        RuntimeSettingsService.DiscoverySettings limits
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(limits.maxConcurrency(), sources.size()),
            Thread.ofPlatform().name("schema-discovery-", 0).factory());
        List<Future<DiscoverySourceAnalyzer.SourceAnalysis>> futures = new ArrayList<>();
        for (PreparedDiscoverySource source : sources) {
            futures.add(executor.submit(() -> AiProfileContext.withProfile(profileId, () -> sourceAnalyzer.analyze(source, request))));
        }
        executor.shutdown();
        long requestDeadline = System.nanoTime() + limits.requestTimeout().toNanos();
        List<DiscoverySourceAnalyzer.SourceAnalysis> successes = new ArrayList<>();
        List<SourceOutcome> outcomes = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            PreparedDiscoverySource source = sources.get(index);
            Future<DiscoverySourceAnalyzer.SourceAnalysis> future = futures.get(index);
            long remainingNanos = Math.max(0, requestDeadline - System.nanoTime());
            long waitNanos = Math.min(limits.sourceTimeout().toNanos(), remainingNanos);
            try {
                if (waitNanos == 0) {
                    throw new TimeoutException("Discovery request deadline exceeded");
                }
                DiscoverySourceAnalyzer.SourceAnalysis analysis = future.get(waitNanos, TimeUnit.NANOSECONDS);
                successes.add(analysis);
                outcomes.add(success(source));
            } catch (TimeoutException exception) {
                future.cancel(true);
                outcomes.add(failure(source, FailureCategory.TIMEOUT));
                log.warn("Schema discovery source failed: sourceId={}, sourceType={}, fingerprint={}, failureCategory={}, retryable=true",
                    source.sourceId(), source.type(), source.fingerprint(), FailureCategory.TIMEOUT);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                outcomes.add(failure(source, FailureCategory.TIMEOUT));
            } catch (ExecutionException exception) {
                FailureCategory category = classify(exception.getCause());
                outcomes.add(failure(source, category));
                log.warn("Schema discovery source failed: sourceId={}, sourceType={}, fingerprint={}, failureCategory={}, retryable={}, exceptionType={}",
                    source.sourceId(), source.type(), source.fingerprint(), category, category.retryable(),
                    LogMetadata.exceptionType(exception.getCause()));
            }
        }
        executor.shutdownNow();
        return new AnalysisBatch(List.copyOf(successes), List.copyOf(outcomes));
    }

    private SourceOutcome success(PreparedDiscoverySource source) {
        return new SourceOutcome(source.sourceId(), source.type(), source.fingerprint(), SourceStatus.SUCCEEDED,
            null, false, source.chunks().size());
    }

    private SourceOutcome failure(PreparedDiscoverySource source, FailureCategory category) {
        return new SourceOutcome(source.sourceId(), source.type(), source.fingerprint(), SourceStatus.FAILED,
            category, category.retryable(), source.chunks().size());
    }

    private FailureCategory classify(Throwable throwable) {
        if (throwable == null) {
            return FailureCategory.PROVIDER_ERROR;
        }
        String type = throwable.getClass().getName().toLowerCase(Locale.ROOT);
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase(Locale.ROOT);
        if (type.contains("timeout") || message.contains("timeout")) {
            return FailureCategory.TIMEOUT;
        }
        if (type.contains("rejected") || message.contains("rate limit") || message.contains("overload")) {
            return FailureCategory.OVERLOADED;
        }
        if (type.contains("json") || message.contains("convert") || message.contains("parse")) {
            return FailureCategory.CONVERSION_ERROR;
        }
        if (type.contains("illegalargument") || message.contains("candidate") || message.contains("validation")) {
            return FailureCategory.VALIDATION_ERROR;
        }
        return FailureCategory.PROVIDER_ERROR;
    }

    private record AnalysisBatch(
        List<DiscoverySourceAnalyzer.SourceAnalysis> successes, List<SourceOutcome> outcomes
    ) {
    }
}
