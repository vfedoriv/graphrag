package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.discovery.application.DiscoveryExecutionPolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionResult.AliasSuggestion;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionAttemptContext;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryAggregator;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.Candidate;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryDeadlineExceededException;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourceAnalyzer;
import io.github.vfedoriv.graphrag.schemas.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureClassifier;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureCode;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureDecision;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceStateException;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AnalysisRunResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.SourceOutcomeResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.SourceOutcomePageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.StartAnalysisResponse;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceResultRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SchemaDraftAnalysisService {
    private final SchemaDraftLifecycleService lifecycleService;
    private final SchemaDraftRepository draftRepository;
    private final SchemaDraftSourceRepository sourceRepository;
    private final SchemaDraftAnalysisRunRepository runRepository;
    private final SchemaDraftSourceResultRepository resultRepository;
    private final SchemaDraftAggregateRevisionRepository aggregateRepository;
    private final SchemaDraftConflictService conflictService;
    private final SchemaDraftAnalysisSourceFactory sourceFactory;
    private final DiscoverySourceAnalyzer sourceAnalyzer;
    private final DiscoveryAggregator aggregator;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftGuidanceMapper guidanceMapper;
    private final ObjectMapper objectMapper;
    private final RuntimeSettingsService runtimeSettingsService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiRuntimeModelFactory modelFactory;
    private final AiObservationService observationService;
    private final SchemaDraftReviewService reviewService;
    private final ThreadPoolTaskExecutor executor;
    private final SchemaDraftWorkflowNavigationService workflowNavigationService;
    private final SchemaDraftAnalysisRetryEligibilityService retryEligibilityService;
    private final SourceFailureClassifier failureClassifier;
    private final String workerId = UUID.randomUUID().toString();

    public SchemaDraftAnalysisService(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftRepository draftRepository,
        SchemaDraftSourceRepository sourceRepository,
        SchemaDraftAnalysisRunRepository runRepository,
        SchemaDraftSourceResultRepository resultRepository,
        SchemaDraftAggregateRevisionRepository aggregateRepository,
        SchemaDraftConflictService conflictService,
        SchemaDraftAnalysisSourceFactory sourceFactory,
        DiscoverySourceAnalyzer sourceAnalyzer,
        DiscoveryAggregator aggregator,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftGuidanceMapper guidanceMapper,
        ObjectMapper objectMapper,
        RuntimeSettingsService runtimeSettingsService,
        KnowledgeBaseService knowledgeBaseService,
        AiRuntimeModelFactory modelFactory,
        AiObservationService observationService,
        SchemaDraftReviewService reviewService,
        SchemaDraftWorkflowNavigationService workflowNavigationService,
        SchemaDraftAnalysisRetryEligibilityService retryEligibilityService,
        SourceFailureClassifier failureClassifier,
        @Qualifier("schemaDraftAnalysisExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.lifecycleService = lifecycleService;
        this.draftRepository = draftRepository;
        this.sourceRepository = sourceRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.aggregateRepository = aggregateRepository;
        this.conflictService = conflictService;
        this.sourceFactory = sourceFactory;
        this.sourceAnalyzer = sourceAnalyzer;
        this.aggregator = aggregator;
        this.jsonSupport = jsonSupport;
        this.guidanceMapper = guidanceMapper;
        this.objectMapper = objectMapper;
        this.runtimeSettingsService = runtimeSettingsService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.modelFactory = modelFactory;
        this.observationService = observationService;
        this.reviewService = reviewService;
        this.workflowNavigationService = workflowNavigationService;
        this.retryEligibilityService = retryEligibilityService;
        this.failureClassifier = failureClassifier;
        this.executor = executor;
    }

    public StartAnalysisResponse start(String knowledgeBaseId, String draftId, long revision) {
        return start(knowledgeBaseId, draftId, revision, null);
    }

    private synchronized StartAnalysisResponse start(
        String knowledgeBaseId, String draftId, long revision, String retryOfRunId
    ) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, revision);
        List<SchemaDraftSourceNode> sources = activeSources(draftId);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one active draft source is required");
        }
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        ChatModel capturedModel = modelFactory.chatModel(profile.getId());
        SchemaDiscoveryRequest request = discoveryRequest(draft);
        String membership = membershipFingerprint(sources);
        RuntimeSettingsService.DiscoverySettings discoverySettings = runtimeSettingsService.discovery();
        String settings = settingsFingerprint(discoverySettings);
        DiscoveryExecutionPolicy policy = DiscoveryExecutionPolicy.from(discoverySettings, settings);
        String snapshot = jsonSupport.fingerprint(String.join("|", Long.toString(draft.getRevision()),
            Long.toString(draft.getGuidanceRevision()), membership, profile.getId(), Long.toString(profile.getRevision()),
            DiscoveryContracts.PROMPT_CONTRACT_REVISION, DiscoveryContracts.CANDIDATE_CONTRACT_REVISION, settings));
        java.util.Optional<SchemaDraftAnalysisRunNode> running = runRepository
            .findFirstByDraftIdAndStatusOrderByCreatedAtDesc(draftId, SchemaDraftAnalysisStatus.RUNNING);
        if (running.isPresent()) {
            if (snapshot.equals(running.get().getSnapshotFingerprint())) {
                return startResponse(knowledgeBaseId, draftId, running.get());
            }
            throw new ConflictException("Schema draft already has a running analysis");
        }
        if (executor.getThreadPoolExecutor().getQueue().remainingCapacity() == 0
            && executor.getActiveCount() >= executor.getMaxPoolSize()) {
            throw new ConflictException("Schema draft analysis queue is full");
        }
        SchemaDraftAnalysisRunNode run = createRun(
            draft, sources, profile, membership, policy, snapshot, retryOfRunId);
        if (!reserveAnalysis(draftId, run.getId(), draft.getRevision())) {
            runRepository.deleteById(run.getId());
            throw new ConflictException("Schema draft already has a running analysis");
        }
        try {
            executor.execute(() -> process(run.getId(), request, capturedModel, policy));
        } catch (RejectedExecutionException exception) {
            draftRepository.releaseAnalysis(draftId, run.getId());
            runRepository.deleteById(run.getId());
            throw new ConflictException("Schema draft analysis queue is full");
        }
        log.info("Schema draft analysis accepted: knowledgeBaseId={}, draftId={}, runId={}, sourceCount={}, draftRevision={}, profileId={}, profileRevision={}, sourceConcurrency={}, sourceTimeoutMs={}, requestTimeoutMs={}",
            knowledgeBaseId, draftId, run.getId(), sources.size(), draft.getRevision(), profile.getId(),
            profile.getRevision(), policy.maxConcurrency(), policy.sourceTimeout().toMillis(),
            policy.requestTimeout().toMillis());
        return startResponse(knowledgeBaseId, draftId, run);
    }

    public StartAnalysisResponse retry(String knowledgeBaseId, String draftId, String runId, long revision) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, revision);
        SchemaDraftAnalysisRunNode prior = requireRun(draftId, runId);
        validateRetryEligibility(prior, retryEligibilityService.inputs(draft));
        return start(knowledgeBaseId, draftId, revision, prior.getId());
    }

    public AnalysisRunPageResponse list(String knowledgeBaseId, String draftId, int page, int size) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        return workflowNavigationService.analysisPage(draft, page, size);
    }

    public AnalysisRunResponse get(
        String knowledgeBaseId, String draftId, String runId, int page, int size
    ) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        SchemaDraftAnalysisRunNode run = requireRun(draftId, runId);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Page<SchemaDraftSourceResultNode> outcomes = resultRepository.findPageByRunId(
            runId, PageRequest.of(Math.max(0, page), boundedSize));
        SchemaDraftAnalysisRetryEligibilityService.EligibilityDecision retryDecision =
            retryEligibilityService.decide(run, retryEligibilityService.inputs(draft));
        return toResponse(run, retryDecision.canRetry(), Math.max(0, page), boundedSize,
            outcomes.getContent(), outcomes.getTotalElements());
    }

    private SchemaDraftAnalysisRunNode createRun(
        SchemaDraftNode draft, List<SchemaDraftSourceNode> sources, AiProfileNode profile,
        String membership, DiscoveryExecutionPolicy policy, String snapshot, String retryOfRunId
    ) {
        Instant now = Instant.now();
        SchemaDraftAnalysisRunNode run = new SchemaDraftAnalysisRunNode();
        run.setId(UUID.randomUUID().toString());
        run.setDraftId(draft.getId());
        run.setKnowledgeBaseId(draft.getKnowledgeBaseId());
        run.setStatus(SchemaDraftAnalysisStatus.RUNNING);
        run.setDraftRevision(draft.getRevision());
        run.setGuidanceRevision(draft.getGuidanceRevision());
        run.setGuidanceFingerprint(draft.getGuidanceFingerprint());
        run.setSourceSnapshotJson(jsonSupport.canonical(sources.stream().map(SourceSnapshot::from).toList()));
        run.setSourceMembershipFingerprint(membership);
        run.setAiProfileId(profile.getId());
        run.setAiProfileRevision(profile.getRevision());
        run.setConfiguredTimeoutSeconds(profile.getTimeoutSeconds());
        run.setConfiguredSdkMaxRetries(profile.getMaxRetries());
        run.setPromptRevision(DiscoveryContracts.PROMPT_CONTRACT_REVISION);
        run.setCandidateRevision(DiscoveryContracts.CANDIDATE_CONTRACT_REVISION);
        run.setSettingsFingerprint(policy.settingsFingerprint());
        run.setDiscoveryMaxConcurrency(policy.maxConcurrency());
        run.setDiscoverySourceTimeoutMillis(policy.sourceTimeout().toMillis());
        run.setDiscoveryRequestTimeoutMillis(policy.requestTimeout().toMillis());
        run.setSnapshotFingerprint(snapshot);
        run.setRetryOfRunId(retryOfRunId);
        run.setTotalSources(sources.size());
        run.setCreatedAt(now);
        run.setStartedAt(now);
        SchemaDraftAnalysisRunNode saved = runRepository.save(run);
        return saved;
    }

    private boolean reserveAnalysis(String draftId, String runId, long expectedRevision) {
        try {
            return Long.valueOf(1).equals(draftRepository.reserveAnalysis(draftId, runId, expectedRevision));
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private void process(
        String runId, SchemaDiscoveryRequest request, ChatModel capturedModel, DiscoveryExecutionPolicy policy
    ) {
        Instant claimedAt = Instant.now();
        if (!Long.valueOf(1).equals(runRepository.claim(runId, workerId, claimedAt))) {
            return;
        }
        long requestStartNanos = System.nanoTime();
        SchemaDraftAnalysisRunNode run = runRepository.findById(runId).orElseThrow();
        Map<String, String> attributes = Map.of("ai.draft.id", run.getDraftId(), "ai.draft.run_id", runId,
            "ai.draft.source_count", Integer.toString(run.getTotalSources()));
        try (AiObservationScope workflow = observationService.startWorkflow(
            new AiWorkflowContext(AiObservationService.WORKFLOW_SCHEMA_DISCOVERY, null, attributes))) {
            try {
                warnProviderEnvelope(run, policy);
                List<DiscoverySourceAnalyzer.SourceAnalysis> successes = analyzeSources(
                    run, request, capturedModel, policy, requestStartNanos);
                finish(run, request, successes);
                workflow.success();
            } catch (RuntimeException exception) {
                failRun(run, exception);
                workflow.error(exception);
            } finally {
                draftRepository.releaseAnalysis(run.getDraftId(), run.getId());
            }
        }
    }

    private List<DiscoverySourceAnalyzer.SourceAnalysis> analyzeSources(
        SchemaDraftAnalysisRunNode run, SchemaDiscoveryRequest request, ChatModel capturedModel,
        DiscoveryExecutionPolicy policy, long requestStartNanos
    ) {
        List<SourceSnapshot> snapshots = readSnapshots(run.getSourceSnapshotJson());
        int sourceThreads = Math.min(policy.maxConcurrency(), snapshots.size());
        ExecutorService sourceExecutor = Executors.newFixedThreadPool(sourceThreads,
            Thread.ofPlatform().daemon(true).name("schema-draft-source-" + run.getId() + "-", 0).factory());
        CompletionService<SourceTaskResult> completion = new ExecutorCompletionService<>(sourceExecutor);
        Map<Future<SourceTaskResult>, SourceTaskControl> controlsByFuture = new HashMap<>();
        List<SourceTaskControl> controls = new ArrayList<>();
        long requestDeadlineNanos = deadlineAfter(requestStartNanos, policy.requestTimeout());
        Map<String, DiscoverySourceAnalyzer.SourceAnalysis> successesBySource = new HashMap<>();
        SchedulingDiagnostics diagnostics = new SchedulingDiagnostics();
        try {
            for (SourceSnapshot snapshot : snapshots) {
                SourceTaskControl control = new SourceTaskControl(new SourceTaskInput(snapshot));
                Future<SourceTaskResult> future = completion.submit(
                    () -> executeSourceTask(
                        control, run, request, capturedModel, policy, requestDeadlineNanos, diagnostics));
                control.future(future);
                controls.add(control);
                controlsByFuture.put(future, control);
                diagnostics.queued.incrementAndGet();
            }
            coordinateSourceTasks(run, completion, controlsByFuture, controls, successesBySource,
                policy, requestDeadlineNanos, diagnostics);
        } finally {
            sourceExecutor.shutdownNow();
        }
        log.info("Schema draft source scheduling completed: draftId={}, runId={}, sourceCount={}, sourceConcurrency={}, globalRunConcurrency={}, providerConcurrencyBound={}, queued={}, started={}, accepted={}, sourceTimedOut={}, requestTimedOut={}, cancellationRequested={}, lateResults={}, elapsedMs={}",
            run.getDraftId(), run.getId(), snapshots.size(), policy.maxConcurrency(), executor.getMaxPoolSize(),
            policy.maxConcurrency() * executor.getMaxPoolSize(), diagnostics.queued.get(), diagnostics.started.get(),
            diagnostics.accepted.get(), diagnostics.sourceTimedOut.get(), diagnostics.requestTimedOut.get(),
            diagnostics.cancellationRequested.get(), diagnostics.lateResults.get(),
            TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - requestStartNanos)));
        return snapshots.stream().map(SourceSnapshot::id).map(successesBySource::get)
            .filter(java.util.Objects::nonNull).toList();
    }

    private SourceTaskResult executeSourceTask(
        SourceTaskControl control, SchemaDraftAnalysisRunNode run, SchemaDiscoveryRequest request,
        ChatModel capturedModel, DiscoveryExecutionPolicy policy, long requestDeadlineNanos,
        SchedulingDiagnostics diagnostics
    ) {
        long startedNanos = System.nanoTime();
        if (!control.start(startedNanos, deadlineAfter(startedNanos, policy.sourceTimeout()))) {
            throw new CancellationException("Source task was closed before start");
        }
        diagnostics.started.incrementAndGet();
        SourceSnapshot snapshot = control.input().snapshot();
        SchemaDraftSourceNode source = sourceRepository.findByIdAndDraftId(snapshot.id(), run.getDraftId()).orElse(null);
        if (source == null || source.getRevision() != snapshot.revision()
            || !java.util.Objects.equals(source.getSha256(), snapshot.sha256())) {
            SourceStateException exception = new SourceStateException(SourceFailureCode.SOURCE_STALE);
            return SourceTaskResult.failure(snapshot, source, failureClassifier.classify(exception), 0,
                exception, System.nanoTime());
        }
        String reuseKey = reuseKey(run, snapshot);
        java.util.Optional<SchemaDraftSourceResultNode> reusable = resultRepository
            .findFirstByDraftIdAndReuseKeyAndStatusOrderByCompletedAtDesc(
                run.getDraftId(), reuseKey, SchemaDraftSourceResultStatus.SUCCEEDED);
        PreparedDiscoverySource prepared = null;
        try {
            requireTaskBudget(control, requestDeadlineNanos);
            prepared = sourceFactory.prepare(source);
            requireTaskBudget(control, requestDeadlineNanos);
            if (reusable.isPresent()) {
                StoredAnalysis stored = jsonSupport.read(reusable.get().getCandidatesJson(), StoredAnalysis.class);
                DiscoverySourceAnalyzer.SourceAnalysis analysis = new DiscoverySourceAnalyzer.SourceAnalysis(
                    prepared, stored.candidates(), stored.aliases());
                return SourceTaskResult.success(snapshot, source, reuseKey, analysis, true, System.nanoTime());
            }
            PreparedDiscoverySource preparedForAnalysis = prepared;
            CandidateExtractionAttemptContext attemptContext = new CandidateExtractionAttemptContext(
                run.getDraftId(), run.getId(), snapshot.id(), snapshot.revision(), null,
                run.getAiProfileId(), run.getAiProfileRevision(), null,
                run.getConfiguredTimeoutSeconds(), run.getConfiguredSdkMaxRetries(),
                control.sourceDeadlineNanos(), requestDeadlineNanos);
            DiscoverySourceAnalyzer.SourceAnalysis analysis = AiProfileContext.withCapturedChatModel(
                run.getAiProfileId(), capturedModel,
                () -> sourceAnalyzer.analyze(preparedForAnalysis, request, attemptContext));
            return SourceTaskResult.success(snapshot, source, reuseKey, analysis, false, System.nanoTime());
        } catch (RuntimeException exception) {
            int chunks = prepared == null ? 0 : prepared.chunks().size();
            return SourceTaskResult.failure(snapshot, source, failureClassifier.classify(exception), chunks,
                exception, System.nanoTime());
        }
    }

    private void coordinateSourceTasks(
        SchemaDraftAnalysisRunNode run, CompletionService<SourceTaskResult> completion,
        Map<Future<SourceTaskResult>, SourceTaskControl> controlsByFuture, List<SourceTaskControl> controls,
        Map<String, DiscoverySourceAnalyzer.SourceAnalysis> successesBySource, DiscoveryExecutionPolicy policy,
        long requestDeadlineNanos, SchedulingDiagnostics diagnostics
    ) {
        while (terminalCount(controls) < controls.size()) {
            Future<SourceTaskResult> ready = completion.poll();
            if (ready != null) {
                if (!handleCompletedFuture(run, ready, controlsByFuture, controls, successesBySource,
                    requestDeadlineNanos, diagnostics)) {
                    break;
                }
                continue;
            }
            long now = System.nanoTime();
            if (now >= requestDeadlineNanos) {
                closeForRequestDeadline(run, controls, diagnostics);
            } else {
                closeExpiredSources(run, controls, now, diagnostics);
            }
            if (terminalCount(controls) >= controls.size()) {
                break;
            }
            try {
                Future<SourceTaskResult> completed = completion.poll(
                    waitNanos(controls, requestDeadlineNanos), TimeUnit.NANOSECONDS);
                if (completed == null) {
                    continue;
                }
                if (!handleCompletedFuture(run, completed, controlsByFuture, controls, successesBySource,
                    requestDeadlineNanos, diagnostics)) {
                    break;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                closeForRequestDeadline(run, controls, diagnostics);
                break;
            }
        }
    }

    private boolean handleCompletedFuture(
        SchemaDraftAnalysisRunNode run, Future<SourceTaskResult> completed,
        Map<Future<SourceTaskResult>, SourceTaskControl> controlsByFuture, List<SourceTaskControl> controls,
        Map<String, DiscoverySourceAnalyzer.SourceAnalysis> successesBySource, long requestDeadlineNanos,
        SchedulingDiagnostics diagnostics
    ) {
        SourceTaskControl control = controlsByFuture.get(completed);
        try {
            SourceTaskResult result = completed.get();
            acceptSourceResult(run, control, result, successesBySource, requestDeadlineNanos, diagnostics);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            closeForRequestDeadline(run, controls, diagnostics);
            return false;
        } catch (CancellationException exception) {
            if (control != null && control.state().terminal()) {
                diagnostics.lateResults.incrementAndGet();
            }
        } catch (ExecutionException exception) {
            if (control != null && control.accept()) {
                RuntimeException failure = exception.getCause() instanceof RuntimeException runtimeException
                    ? runtimeException : new IllegalStateException("Source task failed", exception.getCause());
                SourceTaskResult result = SourceTaskResult.failure(control.input().snapshot(), null,
                    failureClassifier.classify(failure), 0, failure, System.nanoTime());
                persistAcceptedResult(run, result, successesBySource);
                diagnostics.accepted.incrementAndGet();
            }
        }
        return true;
    }

    private void acceptSourceResult(
        SchemaDraftAnalysisRunNode run, SourceTaskControl control, SourceTaskResult result,
        Map<String, DiscoverySourceAnalyzer.SourceAnalysis> successesBySource, long requestDeadlineNanos,
        SchedulingDiagnostics diagnostics
    ) {
        if (control == null || control.state().terminal()) {
            diagnostics.lateResults.incrementAndGet();
            return;
        }
        if (result.completedNanos() >= requestDeadlineNanos) {
            closeTimedOut(run, control, SourceTaskState.REQUEST_TIMED_OUT,
                SourceFailureCode.REQUEST_DEADLINE_EXCEEDED, diagnostics);
            diagnostics.lateResults.incrementAndGet();
            return;
        }
        if (result.completedNanos() >= control.sourceDeadlineNanos()) {
            closeTimedOut(run, control, SourceTaskState.SOURCE_TIMED_OUT,
                SourceFailureCode.SOURCE_DEADLINE_EXCEEDED, diagnostics);
            diagnostics.lateResults.incrementAndGet();
            return;
        }
        if (control.accept()) {
            persistAcceptedResult(run, result, successesBySource);
            diagnostics.accepted.incrementAndGet();
        } else {
            diagnostics.lateResults.incrementAndGet();
        }
    }

    private void persistAcceptedResult(
        SchemaDraftAnalysisRunNode run, SourceTaskResult result,
        Map<String, DiscoverySourceAnalyzer.SourceAnalysis> successesBySource
    ) {
        if (result.analysis() != null && result.source() != null) {
            persistSuccess(run, result.source(), result.reuseKey(), result.analysis(), result.reused());
            successesBySource.put(result.snapshot().id(), result.analysis());
            return;
        }
        updateSourceState(result.source(), result.failure());
        persistFailure(run, result.snapshot(), result.decision(), result.chunkCount(), result.failure());
    }

    private void closeExpiredSources(
        SchemaDraftAnalysisRunNode run, List<SourceTaskControl> controls, long now,
        SchedulingDiagnostics diagnostics
    ) {
        for (SourceTaskControl control : controls) {
            if (control.state() == SourceTaskState.RUNNING && now >= control.sourceDeadlineNanos()) {
                closeTimedOut(run, control, SourceTaskState.SOURCE_TIMED_OUT,
                    SourceFailureCode.SOURCE_DEADLINE_EXCEEDED, diagnostics);
            }
        }
    }

    private void closeForRequestDeadline(
        SchemaDraftAnalysisRunNode run, List<SourceTaskControl> controls, SchedulingDiagnostics diagnostics
    ) {
        for (SourceTaskControl control : controls) {
            if (control.state() == SourceTaskState.QUEUED || control.state() == SourceTaskState.RUNNING) {
                closeTimedOut(run, control, SourceTaskState.REQUEST_TIMED_OUT,
                    SourceFailureCode.REQUEST_DEADLINE_EXCEEDED, diagnostics);
            }
        }
    }

    private void closeTimedOut(
        SchemaDraftAnalysisRunNode run, SourceTaskControl control, SourceTaskState timeoutState,
        SourceFailureCode failureCode, SchedulingDiagnostics diagnostics
    ) {
        if (!control.timeout(timeoutState)) {
            return;
        }
        DiscoveryDeadlineExceededException failure = new DiscoveryDeadlineExceededException(failureCode);
        persistFailure(run, control.input().snapshot(), failureClassifier.classify(failure), 0, failure);
        if (timeoutState == SourceTaskState.SOURCE_TIMED_OUT) {
            diagnostics.sourceTimedOut.incrementAndGet();
        } else {
            diagnostics.requestTimedOut.incrementAndGet();
        }
        Future<SourceTaskResult> future = control.future();
        if (future != null) {
            future.cancel(true);
            diagnostics.cancellationRequested.incrementAndGet();
        }
        log.warn("Schema draft source deadline closed: draftId={}, runId={}, sourceId={}, sourceRevision={}, taskState={}, sourceTimeoutMs={}, requestTimeoutMs={}, cancellationRequested=true, elapsedMs={}",
            run.getDraftId(), run.getId(), control.input().snapshot().id(), control.input().snapshot().revision(),
            timeoutState, run.getDiscoverySourceTimeoutMillis(), run.getDiscoveryRequestTimeoutMillis(),
            control.startedNanos() == 0 ? 0
                : TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - control.startedNanos())));
    }

    private void requireTaskBudget(SourceTaskControl control, long requestDeadlineNanos) {
        long now = System.nanoTime();
        if (now >= requestDeadlineNanos) {
            throw new DiscoveryDeadlineExceededException(SourceFailureCode.REQUEST_DEADLINE_EXCEEDED);
        }
        if (now >= control.sourceDeadlineNanos()) {
            throw new DiscoveryDeadlineExceededException(SourceFailureCode.SOURCE_DEADLINE_EXCEEDED);
        }
    }

    private void updateSourceState(SchemaDraftSourceNode source, RuntimeException exception) {
        if (source == null || exception == null) {
            return;
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("stale")) {
            source.setStatus(SchemaDraftSourceStatus.STALE);
            sourceRepository.save(source);
        } else if (message.contains("unavailable")) {
            source.setStatus(SchemaDraftSourceStatus.UNAVAILABLE);
            sourceRepository.save(source);
        }
    }

    private int terminalCount(List<SourceTaskControl> controls) {
        return (int) controls.stream().filter(control -> control.state().terminal()).count();
    }

    private long waitNanos(List<SourceTaskControl> controls, long requestDeadlineNanos) {
        long now = System.nanoTime();
        long nearest = requestDeadlineNanos;
        for (SourceTaskControl control : controls) {
            if (control.state() == SourceTaskState.RUNNING) {
                nearest = Math.min(nearest, control.sourceDeadlineNanos());
            }
        }
        long remaining = Math.max(1, nearest - now);
        return Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100));
    }

    private long deadlineAfter(long startNanos, java.time.Duration timeout) {
        long durationNanos;
        try {
            durationNanos = timeout.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
        if (durationNanos > Long.MAX_VALUE - startNanos) {
            return Long.MAX_VALUE;
        }
        return startNanos + durationNanos;
    }

    private void warnProviderEnvelope(SchemaDraftAnalysisRunNode run, DiscoveryExecutionPolicy policy) {
        long attempts = Math.max(1L, (long) run.getConfiguredSdkMaxRetries() + 1L);
        long envelopeSeconds;
        try {
            envelopeSeconds = Math.multiplyExact((long) run.getConfiguredTimeoutSeconds(), attempts);
        } catch (ArithmeticException exception) {
            envelopeSeconds = Long.MAX_VALUE;
        }
        long sourceSeconds = policy.sourceTimeout().toSeconds();
        long requestSeconds = policy.requestTimeout().toSeconds();
        if (envelopeSeconds > sourceSeconds || envelopeSeconds > requestSeconds) {
            log.warn("AI provider retry envelope exceeds schema draft workflow budget: draftId={}, runId={}, profileId={}, profileRevision={}, configuredTimeoutSeconds={}, configuredSdkMaxRetries={}, providerEnvelopeSeconds={}, sourceTimeoutMs={}, requestTimeoutMs={}",
                run.getDraftId(), run.getId(), run.getAiProfileId(), run.getAiProfileRevision(),
                run.getConfiguredTimeoutSeconds(), run.getConfiguredSdkMaxRetries(), envelopeSeconds,
                policy.sourceTimeout().toMillis(), policy.requestTimeout().toMillis());
        }
    }

    private void finish(
        SchemaDraftAnalysisRunNode run, SchemaDiscoveryRequest request,
        List<DiscoverySourceAnalyzer.SourceAnalysis> successes
    ) {
        List<SchemaDraftSourceResultNode> outcomes = resultRepository.findByRunIdOrderByCreatedAtAsc(run.getId());
        int succeeded = (int) outcomes.stream().filter(value -> value.getStatus() == SchemaDraftSourceResultStatus.SUCCEEDED).count();
        int failed = outcomes.size() - succeeded;
        run.setSucceededSources(succeeded);
        run.setFailedSources(failed);
        run.setCompletedAt(Instant.now());
        if (succeeded == 0) {
            run.setStatus(SchemaDraftAnalysisStatus.FAILED);
            run.setFailureCategory("ALL_SOURCES_FAILED");
            run.setRetryable(outcomes.stream().anyMatch(SchemaDraftSourceResultNode::isRetryable));
            runRepository.save(run);
            return;
        }
        DiscoveryAggregator.AggregateResult aggregate = aggregator.aggregate(successes, request);
        SchemaDraftAggregateRevisionNode revision = persistAggregate(run, aggregate, failed > 0);
        persistConflicts(run.getDraftId(), revision.getId(), aggregate);
        reviewService.reconcileAfterAnalysis(run.getDraftId(), revision.getId());
        run.setAggregateRevisionId(revision.getId());
        run.setStatus(failed == 0 ? SchemaDraftAnalysisStatus.COMPLETED : SchemaDraftAnalysisStatus.PARTIAL);
        run.setRetryable(failed > 0 && outcomes.stream()
            .filter(value -> value.getStatus() == SchemaDraftSourceResultStatus.FAILED)
            .anyMatch(SchemaDraftSourceResultNode::isRetryable));
        boolean current = promoteIfCurrent(run, revision);
        run.setCurrentResult(current);
        runRepository.save(run);
        log.info("Schema draft analysis completed: draftId={}, runId={}, status={}, succeededSources={}, failedSources={}, currentResult={}, aggregateRevisionId={}",
            run.getDraftId(), run.getId(), run.getStatus(), succeeded, failed, current, revision.getId());
    }

    private SchemaDraftAggregateRevisionNode persistAggregate(
        SchemaDraftAnalysisRunNode run, DiscoveryAggregator.AggregateResult aggregate, boolean partial
    ) {
        long next = aggregateRepository.findFirstByDraftIdOrderByRevisionDesc(run.getDraftId())
            .map(value -> value.getRevision() + 1).orElse(1L);
        SchemaDraftAggregateRevisionNode revision = new SchemaDraftAggregateRevisionNode();
        revision.setId(UUID.randomUUID().toString());
        revision.setDraftId(run.getDraftId());
        revision.setRunId(run.getId());
        revision.setRevision(next);
        revision.setCandidatesJson(jsonSupport.canonical(aggregate.candidates()));
        revision.setConflictsJson(jsonSupport.canonical(aggregate.conflicts()));
        revision.setWarningsJson(jsonSupport.canonical(aggregate.warnings()));
        revision.setSchemaJson(jsonSupport.canonical(aggregate.schema()));
        revision.setContentHash(jsonSupport.fingerprint(revision.getSchemaJson()));
        revision.setPartial(partial);
        revision.setCreatedAt(Instant.now());
        SchemaDraftAggregateRevisionNode saved = aggregateRepository.save(revision);
        return saved;
    }

    private void persistConflicts(
        String draftId, String aggregateId, DiscoveryAggregator.AggregateResult aggregate
    ) {
        aggregate.conflicts().forEach(value -> {
            conflictService.create(draftId, aggregateId, conflictType(value.category().name()),
                value.coordinate(), value.alternatives(), value.evidence());
        });
    }

    private boolean promoteIfCurrent(SchemaDraftAnalysisRunNode run, SchemaDraftAggregateRevisionNode aggregate) {
        SchemaDraftNode draft = draftRepository.findById(run.getDraftId()).orElse(null);
        if (draft == null || draft.getRevision() != run.getDraftRevision()) {
            return false;
        }
        if (!run.getId().equals(draft.getRunningAnalysisRunId())) {
            return false;
        }
        if (!membershipFingerprint(activeSources(run.getDraftId())).equals(run.getSourceMembershipFingerprint())) {
            return false;
        }
        return reviewService.promoteIfRevisionCurrent(run.getDraftId(), aggregate.getId(), run.getDraftRevision());
    }

    private void persistSuccess(
        SchemaDraftAnalysisRunNode run, SchemaDraftSourceNode source, String reuseKey,
        DiscoverySourceAnalyzer.SourceAnalysis analysis, boolean reused
    ) {
        SchemaDraftSourceResultNode result = baseResult(run, SourceSnapshot.from(source));
        result.setReuseKey(reuseKey);
        result.setStatus(SchemaDraftSourceResultStatus.SUCCEEDED);
        result.setReused(reused);
        result.setCandidatesJson(jsonSupport.canonical(new StoredAnalysis(analysis.candidates(), analysis.aliasSuggestions())));
        result.setChunkCount(analysis.source().chunks().size());
        result.setCompletedAt(Instant.now());
        SchemaDraftSourceResultNode saved = resultRepository.save(result);
        source.setAnalyzed(true);
        sourceRepository.save(source);
    }

    private void persistFailure(
        SchemaDraftAnalysisRunNode run, SourceSnapshot source, SourceFailureDecision decision, int chunks,
        RuntimeException exception
    ) {
        SchemaDraftSourceResultNode result = baseResult(run, source);
        result.setReuseKey(reuseKey(run, source));
        result.setStatus(SchemaDraftSourceResultStatus.FAILED);
        result.setFailureCategory(decision.category().name());
        result.setFailureCode(decision.code().name());
        result.setRetryable(decision.retryable());
        result.setChunkCount(chunks);
        result.setCompletedAt(Instant.now());
        SchemaDraftSourceResultNode saved = resultRepository.save(result);
        log.warn("Schema draft source analysis failed: draftId={}, runId={}, sourceId={}, sourceRevision={}, failureCategory={}, failureCode={}, retryable={}, providerStatus={}, preparedChunkCount={}, exceptionTypes={}, rootExceptionType={}, messageFingerprint={}",
            run.getDraftId(), run.getId(), source.id(), source.revision(), decision.category(), decision.code(),
            decision.retryable(), decision.providerStatus(), chunks, decision.exceptionTypes(),
            decision.rootExceptionType(), decision.messageFingerprint());
    }

    private SchemaDraftSourceResultNode baseResult(SchemaDraftAnalysisRunNode run, SourceSnapshot source) {
        SchemaDraftSourceResultNode result = new SchemaDraftSourceResultNode();
        result.setId(UUID.randomUUID().toString());
        result.setDraftId(run.getDraftId());
        result.setRunId(run.getId());
        result.setSourceId(source.id());
        result.setSourceRevision(source.revision());
        result.setSourceSha256(source.sha256());
        result.setCreatedAt(Instant.now());
        return result;
    }

    private void failRun(SchemaDraftAnalysisRunNode run, RuntimeException exception) {
        run.setStatus(SchemaDraftAnalysisStatus.FAILED);
        run.setFailureCategory(exception.getClass().getSimpleName());
        run.setRetryable(true);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
        log.error("Schema draft analysis failed: draftId={}, runId={}, exceptionType={}",
            run.getDraftId(), run.getId(), exception.getClass().getSimpleName());
    }

    private SchemaDiscoveryRequest discoveryRequest(SchemaDraftNode draft) {
        io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DraftGuidance guidance = guidanceMapper.read(draft.getGuidanceJson());
        return new SchemaDiscoveryRequest(List.of(), List.of(), guidance.additionalInstructions(), guidance.guidance());
    }

    private List<SchemaDraftSourceNode> activeSources(String draftId) {
        return sourceRepository.findByDraftIdAndStatusOrderByCreatedAtAsc(draftId, SchemaDraftSourceStatus.ACTIVE);
    }

    private String membershipFingerprint(List<SchemaDraftSourceNode> sources) {
        String value = sources.stream().sorted(Comparator.comparing(SchemaDraftSourceNode::getId))
            .map(source -> source.getId() + ":" + source.getRevision() + ":" + source.getSha256())
            .reduce((left, right) -> left + "|" + right).orElse("");
        return jsonSupport.fingerprint(value);
    }

    private String settingsFingerprint(RuntimeSettingsService.DiscoverySettings settings) {
        String value = String.join("|",
            Integer.toString(settings.maxSources()),
            Integer.toString(settings.maxSourceBytes()),
            Integer.toString(settings.maxTotalBytes()),
            Integer.toString(settings.maxSourceCharacters()),
            Integer.toString(settings.maxTotalCharacters()),
            Integer.toString(settings.chunkCharacters()),
            Integer.toString(settings.maxChunksPerSource()),
            Integer.toString(settings.maxConcurrency()),
            Long.toString(settings.sourceTimeout().toMillis()),
            Long.toString(settings.requestTimeout().toMillis())
        );
        return jsonSupport.fingerprint(value);
    }

    private String reuseKey(SchemaDraftAnalysisRunNode run, SourceSnapshot source) {
        return jsonSupport.fingerprint(String.join("|", run.getDraftId(), source.id(), Long.toString(source.revision()),
            source.sha256(), Long.toString(run.getGuidanceRevision()), run.getGuidanceFingerprint(), run.getAiProfileId(),
            Long.toString(run.getAiProfileRevision()), run.getPromptRevision(), run.getCandidateRevision(),
            run.getSettingsFingerprint()));
    }

    private List<SourceSnapshot> readSnapshots(String json) {
        try {
            return objectMapper.readerForListOf(SourceSnapshot.class).readValue(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Analysis source snapshot is invalid", exception);
        }
    }

    private SchemaDraftConflictType conflictType(String category) {
        return switch (category) {
            case "PROPERTY_TYPE" -> SchemaDraftConflictType.TYPE;
            case "IDENTITY_KEY" -> SchemaDraftConflictType.KEY;
            case "RELATIONSHIP_NAME" -> SchemaDraftConflictType.RELATIONSHIP_NAME;
            case "RELATIONSHIP_DIRECTION" -> SchemaDraftConflictType.RELATIONSHIP_DIRECTION;
            case "ALIAS_SUGGESTION" -> SchemaDraftConflictType.ALIAS;
            default -> SchemaDraftConflictType.COORDINATE;
        };
    }

    private SchemaDraftAnalysisRunNode requireRun(String draftId, String runId) {
        return runRepository.findByIdAndDraftId(runId, draftId)
            .orElseThrow(() -> new NotFoundException("Schema draft analysis run not found: " + runId));
    }

    private StartAnalysisResponse startResponse(
        String knowledgeBaseId, String draftId, SchemaDraftAnalysisRunNode run
    ) {
        String location = "/api/v1/knowledge-bases/" + knowledgeBaseId + "/schema-drafts/" + draftId
            + "/analysis-runs/" + run.getId();
        return new StartAnalysisResponse(run.getId(), run.getStatus(), location);
    }

    private AnalysisRunResponse toResponse(
        SchemaDraftAnalysisRunNode run, boolean canRetry, int page, int size,
        List<SchemaDraftSourceResultNode> outcomes, long total
    ) {
        List<SourceOutcomeResponse> responses = outcomes.stream().map(value -> new SourceOutcomeResponse(
            value.getId(), value.getSourceId(), value.getSourceRevision(), value.getStatus(), value.isReused(),
            value.getFailureCategory(), value.getFailureCode() == null ? null
                : SourceFailureCode.valueOf(value.getFailureCode()), value.isRetryable(), value.getChunkCount(),
            value.getCompletedAt())).toList();
        return new AnalysisRunResponse(run.getId(), run.getStatus(), run.getDraftRevision(), run.getGuidanceRevision(),
            run.getAiProfileId(), run.getAiProfileRevision(), run.getPromptRevision(), run.getCandidateRevision(),
            run.getDiscoveryMaxConcurrency(), run.getDiscoverySourceTimeoutMillis(),
            run.getDiscoveryRequestTimeoutMillis(),
            run.getTotalSources(), run.getSucceededSources(), run.getFailedSources(),
            workflowNavigationService.isAnalysisCurrent(
                draftRepository.findById(run.getDraftId()).orElse(null), run),
            run.getAggregateRevisionId(), run.getFailureCategory(), run.isRetryable(), canRetry,
            run.getRetryOfRunId(), run.getCreatedAt(),
            run.getStartedAt(), run.getCompletedAt(), new SourceOutcomePageResponse(page, size, total, responses));
    }

    private void validateRetryEligibility(
        SchemaDraftAnalysisRunNode run,
        SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs inputs
    ) {
        SchemaDraftAnalysisRetryEligibilityService.EligibilityDecision decision =
            retryEligibilityService.decide(run, inputs);
        if (decision.canRetry()) {
            return;
        }
        switch (decision.reason()) {
            case RUN_NOT_TERMINAL -> throw new ConflictException("Running analysis cannot be retried");
            case DRAFT_NOT_OPEN -> throw new ConflictException("Schema draft is not open");
            case NO_ACTIVE_SOURCES -> throw new IllegalArgumentException("At least one active draft source is required");
            case ANALYSIS_RUNNING -> throw new ConflictException("Schema draft already has a running analysis");
        }
    }

    private record SourceSnapshot(String id, long revision, String sha256) {
        private static SourceSnapshot from(SchemaDraftSourceNode source) {
            return new SourceSnapshot(source.getId(), source.getRevision(), source.getSha256());
        }
    }

    private record SourceTaskInput(SourceSnapshot snapshot) {
    }

    private record SourceTaskResult(
        SourceSnapshot snapshot,
        SchemaDraftSourceNode source,
        String reuseKey,
        DiscoverySourceAnalyzer.SourceAnalysis analysis,
        boolean reused,
        SourceFailureDecision decision,
        int chunkCount,
        RuntimeException failure,
        long completedNanos
    ) {
        private static SourceTaskResult success(
            SourceSnapshot snapshot, SchemaDraftSourceNode source, String reuseKey,
            DiscoverySourceAnalyzer.SourceAnalysis analysis, boolean reused, long completedNanos
        ) {
            return new SourceTaskResult(
                snapshot, source, reuseKey, analysis, reused, null, analysis.source().chunks().size(),
                null, completedNanos);
        }

        private static SourceTaskResult failure(
            SourceSnapshot snapshot, SchemaDraftSourceNode source, SourceFailureDecision decision,
            int chunkCount, RuntimeException failure, long completedNanos
        ) {
            return new SourceTaskResult(
                snapshot, source, null, null, false, decision, chunkCount, failure, completedNanos);
        }

        @Override
        public String toString() {
            return "SourceTaskResult[sourceId=" + snapshot.id()
                + ", sourceRevision=" + snapshot.revision()
                + ", reused=" + reused
                + ", succeeded=" + (analysis != null)
                + ", failureCode=" + (decision == null ? null : decision.code())
                + ", chunkCount=" + chunkCount
                + ", completedNanos=" + completedNanos + "]";
        }
    }

    private enum SourceTaskState {
        QUEUED(false),
        RUNNING(false),
        ACCEPTED(true),
        SOURCE_TIMED_OUT(true),
        REQUEST_TIMED_OUT(true);

        private final boolean terminal;

        SourceTaskState(boolean terminal) {
            this.terminal = terminal;
        }

        private boolean terminal() {
            return terminal;
        }
    }

    private static final class SourceTaskControl {
        private final SourceTaskInput input;
        private final AtomicReference<SourceTaskState> state = new AtomicReference<>(SourceTaskState.QUEUED);
        private final AtomicLong startedNanos = new AtomicLong();
        private final AtomicLong sourceDeadlineNanos = new AtomicLong(Long.MAX_VALUE);
        private volatile Future<SourceTaskResult> future;

        private SourceTaskControl(SourceTaskInput input) {
            this.input = input;
        }

        private boolean start(long started, long deadline) {
            startedNanos.set(started);
            sourceDeadlineNanos.set(deadline);
            return state.compareAndSet(SourceTaskState.QUEUED, SourceTaskState.RUNNING);
        }

        private boolean accept() {
            return state.compareAndSet(SourceTaskState.RUNNING, SourceTaskState.ACCEPTED);
        }

        private boolean timeout(SourceTaskState timeoutState) {
            SourceTaskState current = state.get();
            while (current == SourceTaskState.QUEUED || current == SourceTaskState.RUNNING) {
                if (state.compareAndSet(current, timeoutState)) {
                    return true;
                }
                current = state.get();
            }
            return false;
        }

        private SourceTaskInput input() {
            return input;
        }

        private SourceTaskState state() {
            return state.get();
        }

        private long startedNanos() {
            return startedNanos.get();
        }

        private long sourceDeadlineNanos() {
            return sourceDeadlineNanos.get();
        }

        private Future<SourceTaskResult> future() {
            return future;
        }

        private void future(Future<SourceTaskResult> future) {
            this.future = future;
        }
    }

    private static final class SchedulingDiagnostics {
        private final AtomicLong queued = new AtomicLong();
        private final AtomicLong started = new AtomicLong();
        private final AtomicLong accepted = new AtomicLong();
        private final AtomicLong sourceTimedOut = new AtomicLong();
        private final AtomicLong requestTimedOut = new AtomicLong();
        private final AtomicLong cancellationRequested = new AtomicLong();
        private final AtomicLong lateResults = new AtomicLong();
    }

    private record StoredAnalysis(List<Candidate> candidates, List<AliasSuggestion> aliases) {
        private StoredAnalysis {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
