package io.github.vfedoriv.graphrag.schemas.discovery.application;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseProfiles;
import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryAggregator;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionAttemptContext;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ResponseStatus;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceStatus;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourceAnalyzer;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourcePreparer;
import io.github.vfedoriv.graphrag.schemas.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureClassifier;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureDecision;
import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryResponse;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryResponse.Reproducibility;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryResponse.SourceOutcome;
import io.github.vfedoriv.graphrag.schemas.discovery.api.error.SchemaDiscoveryFailedException;
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
    private final RuntimeSettingsAccess runtimeSettingsService;
    private final KnowledgeBaseProfiles knowledgeBaseService;
    private final AiObservationService observationService;
    private final SourceFailureClassifier failureClassifier;

    public SchemaDiscoveryService(
        DiscoverySourcePreparer sourcePreparer,
        DiscoverySourceAnalyzer sourceAnalyzer,
        DiscoveryAggregator aggregator,
        RuntimeSettingsAccess runtimeSettingsService,
        KnowledgeBaseProfiles knowledgeBaseService,
        AiObservationService observationService,
        SourceFailureClassifier failureClassifier
    ) {
        this.sourcePreparer = sourcePreparer;
        this.sourceAnalyzer = sourceAnalyzer;
        this.aggregator = aggregator;
        this.runtimeSettingsService = runtimeSettingsService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.observationService = observationService;
        this.failureClassifier = failureClassifier;
    }

    public SchemaDiscoveryResponse discover(
        String knowledgeBaseId, SchemaDiscoveryRequest request, List<MultipartFile> files
    ) {
        long startNanos = System.nanoTime();
        List<PreparedDiscoverySource> sources = sourcePreparer.prepare(knowledgeBaseId, request, files);
        RuntimeSettingsAccess.DiscoverySettings limits = runtimeSettingsService.discovery();
        ProfileFacts profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
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
                AnalysisBatch batch = analyze(sources, request, profile, limits);
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
        ProfileFacts profile,
        RuntimeSettingsAccess.DiscoverySettings limits
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(limits.maxConcurrency(), sources.size()),
            Thread.ofPlatform().name("schema-discovery-", 0).factory());
        List<Future<DiscoverySourceAnalyzer.SourceAnalysis>> futures = new ArrayList<>();
        for (PreparedDiscoverySource source : sources) {
            CandidateExtractionAttemptContext context = new CandidateExtractionAttemptContext(
                null, null, source.sourceId(), null, null, profile.getId(), profile.getRevision(), null,
                profile.getTimeoutSeconds(), profile.getMaxRetries(), null, null);
            futures.add(executor.submit(() -> AiProfileContext.withProfile(profile.getId(),
                () -> sourceAnalyzer.analyze(source, request, context))));
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
                SourceFailureDecision decision = failureClassifier.classify(exception);
                outcomes.add(failure(source, decision));
                logFailure(source, decision);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                SourceFailureDecision decision = failureClassifier.classify(
                    new TimeoutException("Discovery source wait was interrupted"));
                outcomes.add(failure(source, decision));
                logFailure(source, decision);
            } catch (ExecutionException exception) {
                SourceFailureDecision decision = failureClassifier.classify(exception.getCause());
                outcomes.add(failure(source, decision));
                logFailure(source, decision);
            }
        }
        executor.shutdownNow();
        return new AnalysisBatch(List.copyOf(successes), List.copyOf(outcomes));
    }

    private SourceOutcome success(PreparedDiscoverySource source) {
        return new SourceOutcome(source.sourceId(), source.type(), source.fingerprint(), SourceStatus.SUCCEEDED,
            null, null, false, source.chunks().size());
    }

    private SourceOutcome failure(PreparedDiscoverySource source, SourceFailureDecision decision) {
        return new SourceOutcome(source.sourceId(), source.type(), source.fingerprint(), SourceStatus.FAILED,
            decision.category(), decision.code(), decision.retryable(), source.chunks().size());
    }

    private void logFailure(PreparedDiscoverySource source, SourceFailureDecision decision) {
        log.warn("Schema discovery source failed: sourceId={}, sourceType={}, fingerprint={}, failureCategory={}, failureCode={}, retryable={}, providerStatus={}, exceptionTypes={}, rootExceptionType={}, messageFingerprint={}",
            source.sourceId(), source.type(), source.fingerprint(), decision.category(), decision.code(),
            decision.retryable(), decision.providerStatus(), decision.exceptionTypes(),
            decision.rootExceptionType(), decision.messageFingerprint());
    }

    private record AnalysisBatch(
        List<DiscoverySourceAnalyzer.SourceAnalysis> successes, List<SourceOutcome> outcomes
    ) {
    }
}
