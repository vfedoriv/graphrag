package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.discovery.CandidateExtractionResult.AliasSuggestion;
import io.github.vfedoriv.graphrag.discovery.DiscoveryAggregator;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.Candidate;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.FailureCategory;
import io.github.vfedoriv.graphrag.discovery.DiscoverySourceAnalyzer;
import io.github.vfedoriv.graphrag.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
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
import io.github.vfedoriv.graphrag.infrastructure.persistence.SchemaDraftGraphService;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftConflictRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceResultRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
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
    private final SchemaDraftConflictRepository conflictRepository;
    private final SchemaDraftGraphService graphService;
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
    private final String workerId = UUID.randomUUID().toString();

    public SchemaDraftAnalysisService(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftRepository draftRepository,
        SchemaDraftSourceRepository sourceRepository,
        SchemaDraftAnalysisRunRepository runRepository,
        SchemaDraftSourceResultRepository resultRepository,
        SchemaDraftAggregateRevisionRepository aggregateRepository,
        SchemaDraftConflictRepository conflictRepository,
        SchemaDraftGraphService graphService,
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
        @Qualifier("schemaDraftAnalysisExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.lifecycleService = lifecycleService;
        this.draftRepository = draftRepository;
        this.sourceRepository = sourceRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.aggregateRepository = aggregateRepository;
        this.conflictRepository = conflictRepository;
        this.graphService = graphService;
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
        String settings = settingsFingerprint();
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
            draft, sources, profile, membership, settings, snapshot, retryOfRunId);
        if (!reserveAnalysis(draftId, run.getId())) {
            runRepository.deleteById(run.getId());
            throw new ConflictException("Schema draft already has a running analysis");
        }
        try {
            executor.execute(() -> process(run.getId(), request, capturedModel));
        } catch (RejectedExecutionException exception) {
            draftRepository.releaseAnalysis(draftId, run.getId());
            runRepository.deleteById(run.getId());
            throw new ConflictException("Schema draft analysis queue is full");
        }
        log.info("Schema draft analysis accepted: knowledgeBaseId={}, draftId={}, runId={}, sourceCount={}, draftRevision={}, profileId={}, profileRevision={}",
            knowledgeBaseId, draftId, run.getId(), sources.size(), draft.getRevision(), profile.getId(), profile.getRevision());
        return startResponse(knowledgeBaseId, draftId, run);
    }

    public StartAnalysisResponse retry(String knowledgeBaseId, String draftId, String runId, long revision) {
        lifecycleService.requireMutable(knowledgeBaseId, draftId, revision);
        SchemaDraftAnalysisRunNode prior = requireRun(draftId, runId);
        if (prior.getStatus() == SchemaDraftAnalysisStatus.RUNNING) {
            throw new ConflictException("Running analysis cannot be retried");
        }
        return start(knowledgeBaseId, draftId, revision, prior.getId());
    }

    public AnalysisRunPageResponse list(String knowledgeBaseId, String draftId, int page, int size) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        return workflowNavigationService.analysisPage(draft, page, size);
    }

    public AnalysisRunResponse get(
        String knowledgeBaseId, String draftId, String runId, int page, int size
    ) {
        lifecycleService.requireOwned(knowledgeBaseId, draftId);
        SchemaDraftAnalysisRunNode run = requireRun(draftId, runId);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Page<SchemaDraftSourceResultNode> outcomes = resultRepository.findPageByRunId(
            runId, PageRequest.of(Math.max(0, page), boundedSize));
        return toResponse(run, Math.max(0, page), boundedSize, outcomes.getContent(), outcomes.getTotalElements());
    }

    private SchemaDraftAnalysisRunNode createRun(
        SchemaDraftNode draft, List<SchemaDraftSourceNode> sources, AiProfileNode profile,
        String membership, String settings, String snapshot, String retryOfRunId
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
        run.setPromptRevision(DiscoveryContracts.PROMPT_CONTRACT_REVISION);
        run.setCandidateRevision(DiscoveryContracts.CANDIDATE_CONTRACT_REVISION);
        run.setSettingsFingerprint(settings);
        run.setSnapshotFingerprint(snapshot);
        run.setRetryOfRunId(retryOfRunId);
        run.setTotalSources(sources.size());
        run.setCreatedAt(now);
        run.setStartedAt(now);
        SchemaDraftAnalysisRunNode saved = runRepository.save(run);
        graphService.attach(draft.getId(), "SchemaDraftAnalysisRun", saved.getId());
        return saved;
    }

    private boolean reserveAnalysis(String draftId, String runId) {
        try {
            return Long.valueOf(1).equals(draftRepository.reserveAnalysis(draftId, runId));
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private void process(String runId, SchemaDiscoveryRequest request, ChatModel capturedModel) {
        if (!Long.valueOf(1).equals(runRepository.claim(runId, workerId, Instant.now()))) {
            return;
        }
        SchemaDraftAnalysisRunNode run = runRepository.findById(runId).orElseThrow();
        Map<String, String> attributes = Map.of("ai.draft.id", run.getDraftId(), "ai.draft.run_id", runId,
            "ai.draft.source_count", Integer.toString(run.getTotalSources()));
        try (AiObservationScope workflow = observationService.startWorkflow(
            new AiWorkflowContext(AiObservationService.WORKFLOW_SCHEMA_DISCOVERY, null, attributes))) {
            try {
                List<DiscoverySourceAnalyzer.SourceAnalysis> successes = analyzeSources(run, request, capturedModel);
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
        SchemaDraftAnalysisRunNode run, SchemaDiscoveryRequest request, ChatModel capturedModel
    ) {
        List<SourceSnapshot> snapshots = readSnapshots(run.getSourceSnapshotJson());
        List<DiscoverySourceAnalyzer.SourceAnalysis> successes = new ArrayList<>();
        for (SourceSnapshot snapshot : snapshots) {
            SchemaDraftSourceNode source = sourceRepository.findByIdAndDraftId(snapshot.id(), run.getDraftId()).orElse(null);
            if (source == null || source.getRevision() != snapshot.revision() || !source.getSha256().equals(snapshot.sha256())) {
                persistFailure(run, snapshot, FailureCategory.VALIDATION_ERROR, false, 0);
                continue;
            }
            String reuseKey = reuseKey(run, snapshot);
            java.util.Optional<SchemaDraftSourceResultNode> reusable = resultRepository
                .findFirstByDraftIdAndReuseKeyAndStatusOrderByCompletedAtDesc(
                    run.getDraftId(), reuseKey, SchemaDraftSourceResultStatus.SUCCEEDED);
            try {
                PreparedDiscoverySource prepared = sourceFactory.prepare(source);
                if (reusable.isPresent()) {
                    StoredAnalysis stored = jsonSupport.read(reusable.get().getCandidatesJson(), StoredAnalysis.class);
                    DiscoverySourceAnalyzer.SourceAnalysis analysis = new DiscoverySourceAnalyzer.SourceAnalysis(
                        prepared, stored.candidates(), stored.aliases());
                    persistSuccess(run, source, reuseKey, analysis, true);
                    successes.add(analysis);
                } else {
                    DiscoverySourceAnalyzer.SourceAnalysis analysis = AiProfileContext.withCapturedChatModel(
                        run.getAiProfileId(), capturedModel, () -> sourceAnalyzer.analyze(prepared, request));
                    persistSuccess(run, source, reuseKey, analysis, false);
                    successes.add(analysis);
                }
            } catch (RuntimeException exception) {
                FailureCategory category = classify(exception);
                String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
                if (message.contains("stale")) {
                    source.setStatus(SchemaDraftSourceStatus.STALE);
                    sourceRepository.save(source);
                } else if (message.contains("unavailable")) {
                    source.setStatus(SchemaDraftSourceStatus.UNAVAILABLE);
                    sourceRepository.save(source);
                }
                persistFailure(run, snapshot, category, category.retryable(), 0);
            }
        }
        return successes;
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
        graphService.attach(run.getDraftId(), "SchemaDraftAggregateRevision", saved.getId());
        return saved;
    }

    private void persistConflicts(
        String draftId, String aggregateId, DiscoveryAggregator.AggregateResult aggregate
    ) {
        aggregate.conflicts().forEach(value -> {
            SchemaDraftConflictNode conflict = new SchemaDraftConflictNode();
            conflict.setId(UUID.randomUUID().toString());
            conflict.setDraftId(draftId);
            conflict.setAggregateRevisionId(aggregateId);
            conflict.setType(conflictType(value.category().name()));
            conflict.setCoordinate(value.coordinate());
            conflict.setAlternativesJson(jsonSupport.canonical(value.alternatives()));
            conflict.setEvidenceJson(jsonSupport.canonical(value.evidence()));
            conflict.setCreatedAt(Instant.now());
            SchemaDraftConflictNode saved = conflictRepository.save(conflict);
            graphService.attach(draftId, "SchemaDraftConflict", saved.getId());
        });
    }

    private boolean promoteIfCurrent(SchemaDraftAnalysisRunNode run, SchemaDraftAggregateRevisionNode aggregate) {
        SchemaDraftNode draft = draftRepository.findById(run.getDraftId()).orElse(null);
        if (draft == null || draft.getRevision() != run.getDraftRevision()) {
            return false;
        }
        if (!membershipFingerprint(activeSources(run.getDraftId())).equals(run.getSourceMembershipFingerprint())) {
            return false;
        }
        draft.setCurrentAggregateId(aggregate.getId());
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);
        return true;
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
        graphService.attach(run.getDraftId(), "SchemaDraftSourceResult", saved.getId());
        source.setAnalyzed(true);
        sourceRepository.save(source);
    }

    private void persistFailure(
        SchemaDraftAnalysisRunNode run, SourceSnapshot source, FailureCategory category, boolean retryable, int chunks
    ) {
        SchemaDraftSourceResultNode result = baseResult(run, source);
        result.setReuseKey(reuseKey(run, source));
        result.setStatus(SchemaDraftSourceResultStatus.FAILED);
        result.setFailureCategory(category.name());
        result.setRetryable(retryable);
        result.setChunkCount(chunks);
        result.setCompletedAt(Instant.now());
        SchemaDraftSourceResultNode saved = resultRepository.save(result);
        graphService.attach(run.getDraftId(), "SchemaDraftSourceResult", saved.getId());
        log.warn("Schema draft source analysis failed: draftId={}, runId={}, sourceId={}, sourceRevision={}, failureCategory={}, retryable={}",
            run.getDraftId(), run.getId(), source.id(), source.revision(), category, retryable);
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

    private String settingsFingerprint() {
        RuntimeSettingsService.DiscoverySettings settings = runtimeSettingsService.discovery();
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

    private FailureCategory classify(RuntimeException exception) {
        String value = (exception.getClass().getName() + " " + exception.getMessage()).toLowerCase(Locale.ROOT);
        if (value.contains("timeout")) return FailureCategory.TIMEOUT;
        if (value.contains("reject") || value.contains("overload") || value.contains("rate limit")) return FailureCategory.OVERLOADED;
        if (value.contains("json") || value.contains("convert") || value.contains("parse")) return FailureCategory.CONVERSION_ERROR;
        if (value.contains("stale") || value.contains("unavailable") || value.contains("validation")) return FailureCategory.VALIDATION_ERROR;
        return FailureCategory.PROVIDER_ERROR;
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
        SchemaDraftAnalysisRunNode run, int page, int size,
        List<SchemaDraftSourceResultNode> outcomes, long total
    ) {
        List<SourceOutcomeResponse> responses = outcomes.stream().map(value -> new SourceOutcomeResponse(
            value.getId(), value.getSourceId(), value.getSourceRevision(), value.getStatus(), value.isReused(),
            value.getFailureCategory(), value.isRetryable(), value.getChunkCount(), value.getCompletedAt())).toList();
        return new AnalysisRunResponse(run.getId(), run.getStatus(), run.getDraftRevision(), run.getGuidanceRevision(),
            run.getAiProfileId(), run.getAiProfileRevision(), run.getPromptRevision(), run.getCandidateRevision(),
            run.getTotalSources(), run.getSucceededSources(), run.getFailedSources(),
            workflowNavigationService.isAnalysisCurrent(
                draftRepository.findById(run.getDraftId()).orElse(null), run),
            run.getAggregateRevisionId(), run.getFailureCategory(), run.isRetryable(), run.getRetryOfRunId(), run.getCreatedAt(),
            run.getStartedAt(), run.getCompletedAt(), new SourceOutcomePageResponse(page, size, total, responses));
    }

    private record SourceSnapshot(String id, long revision, String sha256) {
        private static SourceSnapshot from(SchemaDraftSourceNode source) {
            return new SourceSnapshot(source.getId(), source.getRevision(), source.getSha256());
        }
    }

    private record StoredAnalysis(List<Candidate> candidates, List<AliasSuggestion> aliases) {
        private StoredAnalysis {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
