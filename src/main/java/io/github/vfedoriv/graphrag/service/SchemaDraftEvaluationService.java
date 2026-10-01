package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.config.SchemaDraftEvaluationProperties;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.AdvisoryAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.CoordinateAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Metrics;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.QuestionAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationOutcomeResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationOutcomePageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationMetricsResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AdvisoryAssessmentResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationRunResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationRunPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ProjectionResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.StartEvaluationRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.StartEvaluationResponse;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.documents.ports.GraphExtractionClient;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionValidationService;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import io.github.vfedoriv.graphrag.documents.application.processing.ChunkingService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentParsingService;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SchemaDraftEvaluationService {
    private static final Duration CLAIM_DURATION = Duration.ofMinutes(30);
    private final SchemaDraftLifecycleService lifecycleService;
    private final SchemaDraftReviewService reviewService;
    private final DocumentUploadRepository documentRepository;
    private final SchemaDraftEvaluationRunRepository runRepository;
    private final SchemaDraftEvaluationOutcomeRepository outcomeRepository;
    private final SchemaDraftAggregateRevisionRepository aggregateRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentUploadService uploadService;
    private final DocumentParsingService parsingService;
    private final ChunkingService chunkingService;
    private final ObjectProvider<GraphExtractionClient> extractionClients;
    private final GraphExtractionValidationService validationService;
    private final SchemaDraftEvaluationMetricsCalculator metricsCalculator;
    private final SchemaParser schemaParser;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftGuidanceMapper guidanceMapper;
    private final SchemaDraftEvaluationContractMapper contractMapper;
    private final ObjectMapper objectMapper;
    private final SchemaDraftEvaluationProperties properties;
    private final AiObservationService observationService;
    private final TaskExecutor executor;
    private final SchemaDraftEvaluationEligibilityService eligibilityService;
    private final SchemaDraftWorkflowNavigationService workflowNavigationService;
    private final SchemaDraftWorkflowCheckpointService checkpointService;

    public SchemaDraftEvaluationService(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftReviewService reviewService,
        DocumentUploadRepository documentRepository,
        SchemaDraftEvaluationRunRepository runRepository,
        SchemaDraftEvaluationOutcomeRepository outcomeRepository,
        SchemaDraftAggregateRevisionRepository aggregateRepository,
        KnowledgeBaseService knowledgeBaseService,
        DocumentUploadService uploadService,
        DocumentParsingService parsingService,
        ChunkingService chunkingService,
        ObjectProvider<GraphExtractionClient> extractionClients,
        GraphExtractionValidationService validationService,
        SchemaDraftEvaluationMetricsCalculator metricsCalculator,
        SchemaParser schemaParser,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftGuidanceMapper guidanceMapper,
        SchemaDraftEvaluationContractMapper contractMapper,
        ObjectMapper objectMapper,
        SchemaDraftEvaluationProperties properties,
        AiObservationService observationService,
        SchemaDraftEvaluationEligibilityService eligibilityService,
        SchemaDraftWorkflowNavigationService workflowNavigationService,
        SchemaDraftWorkflowCheckpointService checkpointService,
        @Qualifier("schemaDraftEvaluationExecutor") TaskExecutor executor
    ) {
        this.lifecycleService = lifecycleService;
        this.reviewService = reviewService;
        this.documentRepository = documentRepository;
        this.runRepository = runRepository;
        this.outcomeRepository = outcomeRepository;
        this.aggregateRepository = aggregateRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.uploadService = uploadService;
        this.parsingService = parsingService;
        this.chunkingService = chunkingService;
        this.extractionClients = extractionClients;
        this.validationService = validationService;
        this.metricsCalculator = metricsCalculator;
        this.schemaParser = schemaParser;
        this.jsonSupport = jsonSupport;
        this.guidanceMapper = guidanceMapper;
        this.contractMapper = contractMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.observationService = observationService;
        this.eligibilityService = eligibilityService;
        this.workflowNavigationService = workflowNavigationService;
        this.checkpointService = checkpointService;
        this.executor = executor;
    }

    public StartEvaluationResponse start(
        String knowledgeBaseId, String draftId, StartEvaluationRequest request
    ) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, request.revision());
        String capturedAggregateId = draft.getCurrentAggregateId();
        List<String> documentIds = distinctIds(request.documentIds());
        if (documentIds.isEmpty()) throw new IllegalArgumentException("At least one held-out document is required");
        SchemaDraftEvaluationEligibilityService.EligibilitySnapshot eligibility = eligibilityService.resolve(draft);
        eligibilityService.requireReady(eligibility);
        List<DocumentSnapshot> snapshots = new ArrayList<>();
        for (String documentId : documentIds) {
            DocumentUploadNode document = documentRepository.findByIdAndKnowledgeBaseId(documentId, knowledgeBaseId)
                .orElseThrow(() -> new NotFoundException("Held-out document not found in knowledge base: " + documentId));
            if (!eligibilityService.isEligible(document, eligibility)) {
                throw new IllegalArgumentException("Document contributed active discovery evidence and is not held out: " + documentId);
            }
            snapshots.add(new DocumentSnapshot(document.getId(), document.getSha256()));
        }
        SchemaDraftNode current = lifecycleService.requireMutable(knowledgeBaseId, draftId, request.revision());
        if (!java.util.Objects.equals(capturedAggregateId, current.getCurrentAggregateId())) {
            throw new ConflictException("Schema draft aggregate changed while evaluation eligibility was checked");
        }
        return createRun(current, snapshots, request.advisoryEnabled(), null);
    }

    public StartEvaluationResponse retry(String knowledgeBaseId, String draftId, String runId, long revision) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, revision);
        SchemaDraftEvaluationRunNode prior = requireRun(draftId, runId);
        if (prior.getStatus() == SchemaDraftEvaluationStatus.QUEUED || prior.getStatus() == SchemaDraftEvaluationStatus.RUNNING) {
            throw new ConflictException("Evaluation is still running: " + runId);
        }
        if (prior.getDraftRevision() != draft.getRevision()) {
            throw new ConflictException("Evaluation retry requires the same draft revision");
        }
        List<DocumentSnapshot> snapshots = readSnapshots(prior.getDocumentSnapshotJson());
        boolean advisory = jsonSupport.parse(prior.getSettingsJson()).path("advisoryEnabled").asBoolean(false);
        return createRun(draft, snapshots, advisory, prior.getId());
    }

    @io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional(readOnly = true)
    public EvaluationRunResponse get(
        String knowledgeBaseId, String draftId, String runId, int page, int size
    ) {
        lifecycleService.requireOwned(knowledgeBaseId, draftId);
        SchemaDraftEvaluationRunNode run = requireRun(draftId, runId);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<SchemaDraftEvaluationOutcomeNode> outcomes = outcomeRepository.findByRunIdOrderByDocumentIdAsc(
            runId, PageRequest.of(boundedPage, boundedSize));
        return toResponse(run, boundedPage, boundedSize, outcomes.getContent(), outcomes.getTotalElements());
    }

    @io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional(readOnly = true)
    public EvaluationRunPageResponse list(String knowledgeBaseId, String draftId, int page, int size) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        return workflowNavigationService.evaluationPage(draft, page, size);
    }

    public EvaluationEligibleDocumentPageResponse eligibleDocuments(
        String knowledgeBaseId, String draftId, int page, int size
    ) {
        return eligibilityService.list(knowledgeBaseId, draftId, page, size);
    }

    private StartEvaluationResponse createRun(
        SchemaDraftNode draft, List<DocumentSnapshot> snapshots, boolean advisoryEnabled, String retryOfRunId
    ) {
        ProjectionResponse projection = reviewService.projection(draft.getKnowledgeBaseId(), draft.getId());
        String projectionJson = jsonSupport.canonical(projection.schema());
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(draft.getKnowledgeBaseId());
        String settingsJson = jsonSupport.canonical(Map.of("advisoryEnabled", advisoryEnabled));
        String documentSnapshotJson = jsonSupport.canonical(snapshots);
        String snapshotFingerprint = jsonSupport.fingerprint(projectionJson + documentSnapshotJson + profile.getId()
            + profile.getRevision() + SchemaDraftEvaluationContracts.CONTRACT_REVISION + settingsJson);
        SchemaDraftEvaluationRunNode run = new SchemaDraftEvaluationRunNode();
        run.setId(UUID.randomUUID().toString());
        run.setDraftId(draft.getId());
        run.setKnowledgeBaseId(draft.getKnowledgeBaseId());
        run.setStatus(SchemaDraftEvaluationStatus.QUEUED);
        run.setDraftRevision(draft.getRevision());
        run.setAggregateRevisionId(projection.aggregateRevisionId());
        run.setProjectionJson(projectionJson);
        run.setProjectionContentHash(jsonSupport.fingerprint(projectionJson));
        run.setGuidanceJson(draft.getGuidanceJson());
        String decisionsJson = jsonSupport.canonical(reviewService.decisions(draft.getKnowledgeBaseId(), draft.getId()));
        run.setDecisionsJson(decisionsJson);
        run.setDecisionsFingerprint(jsonSupport.fingerprint(decisionsJson));
        run.setDocumentSnapshotJson(documentSnapshotJson);
        run.setAiProfileId(profile.getId());
        run.setAiProfileRevision(profile.getRevision());
        run.setPromptRevision(SchemaDraftEvaluationContracts.PROMPT_REVISION);
        run.setContractRevision(SchemaDraftEvaluationContracts.CONTRACT_REVISION);
        run.setSettingsJson(settingsJson);
        run.setSnapshotFingerprint(snapshotFingerprint);
        run.setRetryOfRunId(retryOfRunId);
        run.setTotalDocuments(snapshots.size());
        run.setRetryable(true);
        run.setCreatedAt(Instant.now());
        List<SchemaDraftEvaluationOutcomeNode> outcomes = new ArrayList<>();
        for (DocumentSnapshot snapshot : snapshots) {
            SchemaDraftEvaluationOutcomeNode outcome = new SchemaDraftEvaluationOutcomeNode();
            outcome.setId(UUID.randomUUID().toString());
            outcome.setRunId(run.getId());
            outcome.setDraftId(draft.getId());
            outcome.setDocumentId(snapshot.documentId());
            outcome.setDocumentSha256(snapshot.sha256());
            outcome.setReuseKey(reuseKey(run, snapshot));
            outcome.setStatus(SchemaDraftEvaluationOutcomeStatus.QUEUED);
            outcome.setRetryable(true);
            outcomes.add(outcome);
        }
        SchemaDraftEvaluationRunNode saved = checkpointService.createEvaluation(run, outcomes);
        try {
            executor.execute(() -> execute(saved.getId()));
        } catch (RuntimeException exception) {
            saved.setStatus(SchemaDraftEvaluationStatus.FAILED);
            saved.setFailureCategory("QUEUE_REJECTED");
            saved.setCompletedAt(Instant.now());
            runRepository.save(saved);
            throw new ConflictException("Evaluation queue is full");
        }
        log.info("Schema draft evaluation queued: draftId={}, runId={}, documentCount={}, projectionFingerprint={}",
            draft.getId(), saved.getId(), snapshots.size(), saved.getProjectionContentHash());
        return new StartEvaluationResponse(saved.getId(), saved.getStatus(), statusLocation(saved));
    }

    void execute(String runId) {
        String workerId = UUID.randomUUID().toString();
        Instant claimedAt = Instant.now();
        if (!Long.valueOf(1).equals(
            runRepository.claim(runId, workerId, claimedAt, claimedAt.plus(CLAIM_DURATION)))) return;
        SchemaDraftEvaluationRunNode run = runRepository.findById(runId).orElseThrow();
        long started = System.nanoTime();
        SchemaDocument schema = schemaParser.parse(run.getProjectionJson());
        GraphExtractionClient client = resolveExtractionClient();
        Map<String, String> attributes = Map.of(
            "ai.draft.id", run.getDraftId(), "ai.draft.evaluation_run_id", runId,
            "ai.draft.document_count", Integer.toString(run.getTotalDocuments()),
            "ai.draft.projection_hash", run.getProjectionContentHash());
        try (AiObservationScope workflow = observationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_SCHEMA_DRAFT_EVALUATION, schema.name(), attributes))) {
            try {
                List<SchemaDraftEvaluationOutcomeNode> outcomes = outcomeRepository.findByRunIdOrderByDocumentIdAsc(runId);
                for (SchemaDraftEvaluationOutcomeNode outcome : outcomes) {
                    processOutcome(run, outcome, schema, client);
                }
                finish(run);
                workflow.success();
            } catch (RuntimeException exception) {
                workflow.error(exception);
                throw exception;
            }
        }
        log.info("Schema draft evaluation completed: draftId={}, runId={}, status={}, succeeded={}, failed={}, stale={}, elapsedMs={}",
            run.getDraftId(), run.getId(), run.getStatus(), run.getSucceededDocuments(), run.getFailedDocuments(),
            run.getStaleDocuments(), LogMetadata.elapsedMillis(started));
    }

    private void processOutcome(
        SchemaDraftEvaluationRunNode run, SchemaDraftEvaluationOutcomeNode outcome,
        SchemaDocument schema, GraphExtractionClient client
    ) {
        outcome.setStatus(SchemaDraftEvaluationOutcomeStatus.RUNNING);
        outcome.setStartedAt(Instant.now());
        SchemaDraftEvaluationOutcomeNode runningOutcome = outcomeRepository.save(outcome);
        DocumentUploadNode document = documentRepository.findByIdAndKnowledgeBaseId(
            runningOutcome.getDocumentId(), run.getKnowledgeBaseId()).orElse(null);
        if (document == null || !runningOutcome.getDocumentSha256().equals(document.getSha256())) {
            completeOutcome(
                runningOutcome, SchemaDraftEvaluationOutcomeStatus.STALE_SOURCE, "SOURCE_CHANGED", false);
            return;
        }
        java.util.Optional<SchemaDraftEvaluationOutcomeNode> reusable = outcomeRepository
            .findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDesc(
                run.getDraftId(), runningOutcome.getReuseKey(),
                List.of(SchemaDraftEvaluationOutcomeStatus.SUCCEEDED, SchemaDraftEvaluationOutcomeStatus.REUSED));
        if (reusable.isPresent() && !reusable.get().getId().equals(runningOutcome.getId())) {
            SchemaDraftEvaluationOutcomeNode prior = reusable.get();
            runningOutcome.setReused(true);
            runningOutcome.setReusedFromOutcomeId(prior.getId());
            runningOutcome.setChunkCount(prior.getChunkCount());
            runningOutcome.setMetricsJson(prior.getMetricsJson());
            runningOutcome.setEvidenceCoordinatesJson(prior.getEvidenceCoordinatesJson());
            completeOutcome(runningOutcome, SchemaDraftEvaluationOutcomeStatus.REUSED, null, false);
            return;
        }
        if (client == null) {
            completeOutcome(
                runningOutcome, SchemaDraftEvaluationOutcomeStatus.FAILED, "AI_CLIENT_UNAVAILABLE", true);
            return;
        }
        try {
            byte[] bytes = uploadService.readContent(document.getContentUri());
            String text = parsingService.parse(document.getOriginalFilename(), document.getContentType(), bytes);
            List<String> chunks = chunkingService.split(text);
            List<Metrics> metrics = new ArrayList<>();
            Set<String> coordinates = new LinkedHashSet<>();
            SupportRisk risk = supportRisk(run.getAggregateRevisionId());
            for (String chunk : chunks) {
                GraphExtractionResult raw = AiProfileContext.withProfile(run.getAiProfileId(), () -> client.extract(schema, chunk));
                GraphExtractionResult sanitized = validationService.validate(raw, schema);
                metrics.add(metricsCalculator.calculate(raw, sanitized, schema, risk.lowSupport(), risk.guidedWithoutEvidence()));
                raw.nodes().forEach(node -> coordinates.add("node:" + node.label()));
                raw.relationships().forEach(relationship -> coordinates.add(
                    "relationship:" + relationship.type() + ":" + relationship.fromLabel() + ":" + relationship.toLabel()));
            }
            runningOutcome.setChunkCount(chunks.size());
            runningOutcome.setMetricsJson(jsonSupport.canonical(contractMapper.metrics(
                metricsCalculator.combine(metrics), List.copyOf(coordinates))));
            runningOutcome.setEvidenceCoordinatesJson(jsonSupport.canonical(coordinates));
            completeOutcome(runningOutcome, SchemaDraftEvaluationOutcomeStatus.SUCCEEDED, null, false);
        } catch (Exception exception) {
            completeOutcome(runningOutcome, SchemaDraftEvaluationOutcomeStatus.FAILED,
                exception.getClass().getSimpleName().toUpperCase(Locale.ROOT), true);
            log.warn("Held-out evaluation document failed: runId={}, documentId={}, exceptionType={}",
                run.getId(), runningOutcome.getDocumentId(), LogMetadata.exceptionType(exception));
        }
    }

    private GraphExtractionClient resolveExtractionClient() {
        List<GraphExtractionClient> clients = extractionClients.orderedStream().toList();
        if (clients.isEmpty()) return null;
        if (clients.size() == 1) return clients.getFirst();
        return clients.stream()
            .filter(client -> !client.getClass().getName().contains("SpringAi"))
            .findFirst()
            .orElse(clients.getFirst());
    }

    private void finish(SchemaDraftEvaluationRunNode run) {
        List<SchemaDraftEvaluationOutcomeNode> outcomes = outcomeRepository.findByRunIdOrderByDocumentIdAsc(run.getId());
        int succeeded = (int) outcomes.stream().filter(value -> value.getStatus() == SchemaDraftEvaluationOutcomeStatus.SUCCEEDED
            || value.getStatus() == SchemaDraftEvaluationOutcomeStatus.REUSED).count();
        int stale = (int) outcomes.stream().filter(value -> value.getStatus() == SchemaDraftEvaluationOutcomeStatus.STALE_SOURCE).count();
        int failed = outcomes.size() - succeeded - stale;
        run.setSucceededDocuments(succeeded);
        run.setFailedDocuments(failed);
        run.setStaleDocuments(stale);
        run.setStatus(succeeded == outcomes.size() ? SchemaDraftEvaluationStatus.COMPLETED
            : succeeded > 0 ? SchemaDraftEvaluationStatus.PARTIAL : SchemaDraftEvaluationStatus.FAILED);
        List<Metrics> metrics = outcomes.stream().filter(value -> value.getMetricsJson() != null)
            .map(value -> contractMapper.domainMetrics(value.getMetricsJson())).toList();
        run.setMetricsJson(jsonSupport.canonical(contractMapper.metrics(metricsCalculator.combine(metrics), List.of())));
        run.setAdvisoryJson(jsonSupport.canonical(contractMapper.advisory(advisory(run), run.getContractRevision())));
        run.setRetryable(run.getStatus() != SchemaDraftEvaluationStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
    }

    private AdvisoryAssessment advisory(SchemaDraftEvaluationRunNode run) {
        if (!jsonSupport.parse(run.getSettingsJson()).path("advisoryEnabled").asBoolean(false)) {
            return new AdvisoryAssessment("NOT_REQUESTED", List.of(), List.of(), run.getAiProfileId(),
                run.getAiProfileRevision(), run.getPromptRevision(), null);
        }
        List<QuestionAssessment> questions = new ArrayList<>();
        List<String> intended = guidanceMapper.read(run.getGuidanceJson()).guidance().intendedQuestions();
        for (String question : intended) {
            questions.add(new QuestionAssessment(jsonSupport.fingerprint(question), "UNASSESSED", List.of()));
        }
        SupportRisk risk = supportRisk(run.getAggregateRevisionId());
        List<CoordinateAssessment> noise = risk.riskyCoordinates().stream()
            .map(value -> new CoordinateAssessment(value, "POSSIBLE_NOISE", "low support or guided without evidence"))
            .toList();
        return new AdvisoryAssessment("COMPLETED_WITHOUT_MODEL_JUDGMENT", questions, noise, run.getAiProfileId(),
            run.getAiProfileRevision(), run.getPromptRevision(), "No advisory model adapter is configured; deterministic results are unaffected");
    }

    private SupportRisk supportRisk(String aggregateRevisionId) {
        JsonNode candidates = jsonSupport.parse(aggregateRepository.findById(aggregateRevisionId).orElseThrow().getCandidatesJson());
        long low = 0;
        long guided = 0;
        List<String> risky = new ArrayList<>();
        for (JsonNode candidate : candidates) {
            boolean lowSupport = candidate.path("supportCount").asInt(0) == 1;
            JsonNode origins = candidate.path("origins");
            boolean hasGuided = contains(origins, "GUIDED");
            boolean hasObserved = contains(origins, "OBSERVED");
            if (lowSupport) low++;
            if (hasGuided && !hasObserved) guided++;
            if (lowSupport || hasGuided && !hasObserved) risky.add(candidate.path("identity").asText());
        }
        return new SupportRisk(low, guided, List.copyOf(risky));
    }

    private boolean contains(JsonNode array, String value) {
        if (!array.isArray()) return false;
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }

    private void completeOutcome(
        SchemaDraftEvaluationOutcomeNode outcome, SchemaDraftEvaluationOutcomeStatus status,
        String failureCategory, boolean retryable
    ) {
        outcome.setStatus(status);
        outcome.setFailureCategory(failureCategory);
        outcome.setRetryable(retryable);
        outcome.setCompletedAt(Instant.now());
        outcomeRepository.save(outcome);
    }

    private EvaluationRunResponse toResponse(
        SchemaDraftEvaluationRunNode run, int page, int size,
        List<SchemaDraftEvaluationOutcomeNode> outcomes, long total
    ) {
        List<EvaluationOutcomeResponse> responses = outcomes.stream().map(this::toResponse).toList();
        EvaluationMetricsResponse metrics = contractMapper.metrics(run.getMetricsJson(), List.of());
        AdvisoryAssessmentResponse advisory = contractMapper.advisory(run.getAdvisoryJson(), run.getContractRevision());
        return new EvaluationRunResponse(run.getId(), run.getStatus(), run.getDraftRevision(), run.getAggregateRevisionId(),
            run.getProjectionContentHash(), run.getAiProfileId(), run.getAiProfileRevision(), run.getPromptRevision(),
            run.getContractRevision(), run.getRetryOfRunId(), run.getTotalDocuments(), run.getSucceededDocuments(),
            run.getFailedDocuments(), run.getStaleDocuments(), metrics, advisory,
            run.getFailureCategory(), run.isRetryable(), run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt(),
            new EvaluationOutcomePageResponse(page, size, total, responses));
    }

    private EvaluationOutcomeResponse toResponse(SchemaDraftEvaluationOutcomeNode outcome) {
        List<String> coordinates = outcome.getEvidenceCoordinatesJson() == null ? List.of()
            : objectMapper.convertValue(jsonSupport.parse(outcome.getEvidenceCoordinatesJson()), new TypeReference<List<String>>() { });
        return new EvaluationOutcomeResponse(outcome.getId(), outcome.getDocumentId(), outcome.getDocumentSha256(),
            outcome.getStatus(), outcome.isReused(), outcome.getChunkCount(),
            contractMapper.metrics(outcome.getMetricsJson(), coordinates), coordinates,
            outcome.getFailureCategory(), outcome.isRetryable(), outcome.getStartedAt(), outcome.getCompletedAt());
    }

    private SchemaDraftEvaluationRunNode requireRun(String draftId, String runId) {
        return runRepository.findByIdAndDraftId(runId, draftId)
            .orElseThrow(() -> new NotFoundException("Schema draft evaluation not found: " + runId));
    }
    private List<String> distinctIds(List<String> ids) {
        if (ids == null) return List.of();
        return ids.stream().map(String::strip).filter(value -> !value.isBlank()).distinct().toList();
    }
    private String reuseKey(SchemaDraftEvaluationRunNode run, DocumentSnapshot snapshot) {
        return jsonSupport.fingerprint(snapshot.documentId() + snapshot.sha256() + run.getProjectionContentHash()
            + run.getAiProfileId() + run.getAiProfileRevision() + run.getPromptRevision()
            + run.getContractRevision() + run.getSettingsJson());
    }
    private List<DocumentSnapshot> readSnapshots(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<DocumentSnapshot>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted evaluation snapshot is invalid", exception);
        }
    }
    private String statusLocation(SchemaDraftEvaluationRunNode run) {
        return "/api/v1/knowledge-bases/" + run.getKnowledgeBaseId() + "/schema-drafts/" + run.getDraftId()
            + "/evaluation-runs/" + run.getId();
    }
    private record DocumentSnapshot(String documentId, String sha256) { }
    private record SupportRisk(long lowSupport, long guidedWithoutEvidence, List<String> riskyCoordinates) { }
}
