package io.github.vfedoriv.graphrag.schemas.evaluation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationContracts;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationContracts.AdvisoryAssessment;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationContracts.CoordinateAssessment;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationContracts.Metrics;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationContracts.QuestionAssessment;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationOutcomeResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationOutcomePageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationMetricsResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.AdvisoryAssessmentResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationRunResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.StartEvaluationRequest;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.StartEvaluationResponse;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.time.Instant;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftAdmissions;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftReviewInputs;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaRegistryCapabilities;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationDocuments;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationDryExtraction;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationProfiles;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.EvaluationObservations.Observation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SchemaDraftEvaluationService {
    private static final Duration CLAIM_DURATION = Duration.ofMinutes(30);
    private final DraftAdmissions admissions;
    private final DraftReviewInputs reviewInputs;
    private final EvaluationDocuments documents;
    private final EvaluationDryExtraction extraction;
    private final SchemaDraftEvaluationRunRepository runRepository;
    private final SchemaDraftEvaluationOutcomeRepository outcomeRepository;
    private final EvaluationProfiles profiles;
    private final SchemaDraftEvaluationMetricsCalculator metricsCalculator;
    private final SchemaRegistryCapabilities registry;
    private final EvaluationJsonSupport jsonSupport;
    private final SchemaDraftEvaluationContractMapper contractMapper;
    private final ObjectMapper objectMapper;
    private final AiObservationService observationService;
    private final TaskExecutor executor;
    private final SchemaDraftEvaluationEligibilityService eligibilityService;
    private final EvaluationHistoryService historyService;
    private final EvaluationCheckpointService checkpointService;

    public SchemaDraftEvaluationService(
        DraftAdmissions admissions, DraftReviewInputs reviewInputs, EvaluationDocuments documents,
        EvaluationDryExtraction extraction, SchemaDraftEvaluationRunRepository runRepository,
        SchemaDraftEvaluationOutcomeRepository outcomeRepository, EvaluationProfiles profiles,
        SchemaDraftEvaluationMetricsCalculator metricsCalculator, SchemaRegistryCapabilities registry,
        EvaluationJsonSupport jsonSupport, SchemaDraftEvaluationContractMapper contractMapper,
        ObjectMapper objectMapper, AiObservationService observationService,
        SchemaDraftEvaluationEligibilityService eligibilityService, EvaluationHistoryService historyService,
        EvaluationCheckpointService checkpointService,
        @Qualifier("schemaDraftEvaluationExecutor") TaskExecutor executor
    ) {
        this.admissions = admissions;
        this.reviewInputs = reviewInputs;
        this.documents = documents;
        this.extraction = extraction;
        this.runRepository = runRepository;
        this.outcomeRepository = outcomeRepository;
        this.profiles = profiles;
        this.metricsCalculator = metricsCalculator;
        this.registry = registry;
        this.jsonSupport = jsonSupport;
        this.contractMapper = contractMapper;
        this.objectMapper = objectMapper;
        this.observationService = observationService;
        this.eligibilityService = eligibilityService;
        this.historyService = historyService;
        this.checkpointService = checkpointService;
        this.executor = executor;
    }

    public StartEvaluationResponse start(
        String knowledgeBaseId, String draftId, StartEvaluationRequest request
    ) {
        DraftAdmissions.Draft draft = admissions.requireMutable(knowledgeBaseId, draftId, request.revision());
        String capturedAggregateId = draft.aggregateRevisionId();
        List<String> documentIds = distinctIds(request.documentIds());
        if (documentIds.isEmpty()) throw new IllegalArgumentException("At least one held-out document is required");
        SchemaDraftEvaluationEligibilityService.EligibilitySnapshot eligibility = eligibilityService.resolve(draft);
        eligibilityService.requireReady(eligibility);
        List<DocumentSnapshot> snapshots = new ArrayList<>();
        for (String documentId : documentIds) {
            EvaluationDocuments.Metadata document = documents.inspectOwned(knowledgeBaseId, documentId)
                .orElseThrow(() -> new NotFoundException("Held-out document not found in knowledge base: " + documentId));
            if (!eligibilityService.isEligible(document, eligibility)) {
                throw new IllegalArgumentException("Document contributed active discovery evidence and is not held out: " + documentId);
            }
            snapshots.add(new DocumentSnapshot(document.documentId(), document.sha256()));
        }
        DraftAdmissions.Draft current = admissions.requireMutable(knowledgeBaseId, draftId, request.revision());
        if (!java.util.Objects.equals(capturedAggregateId, current.aggregateRevisionId())) {
            throw new ConflictException("Schema draft aggregate changed while evaluation eligibility was checked");
        }
        return createRun(current, snapshots, request.advisoryEnabled(), null);
    }

    public StartEvaluationResponse retry(String knowledgeBaseId, String draftId, String runId, long revision) {
        DraftAdmissions.Draft draft = admissions.requireMutable(knowledgeBaseId, draftId, revision);
        SchemaDraftEvaluationRunNode prior = requireRun(draftId, runId);
        if (prior.getStatus() == SchemaDraftEvaluationStatus.QUEUED || prior.getStatus() == SchemaDraftEvaluationStatus.RUNNING) {
            throw new ConflictException("Evaluation is still running: " + runId);
        }
        if (prior.getDraftRevision() != draft.revision()) {
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
        admissions.requireOwned(knowledgeBaseId, draftId);
        SchemaDraftEvaluationRunNode run = requireRun(draftId, runId);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<SchemaDraftEvaluationOutcomeNode> outcomes = outcomeRepository.findByRunIdOrderByDocumentIdAsc(
            runId, PageRequest.of(boundedPage, boundedSize));
        return toResponse(run, boundedPage, boundedSize, outcomes.getContent(), outcomes.getTotalElements());
    }

    @io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional(readOnly = true)
    public EvaluationRunPageResponse list(String knowledgeBaseId, String draftId, int page, int size) {
        DraftAdmissions.Draft draft = admissions.requireOwned(knowledgeBaseId, draftId);
        return historyService.page(draft, page, size);
    }

    public EvaluationEligibleDocumentPageResponse eligibleDocuments(
        String knowledgeBaseId, String draftId, int page, int size
    ) {
        return eligibilityService.list(knowledgeBaseId, draftId, page, size);
    }

    private StartEvaluationResponse createRun(
        DraftAdmissions.Draft draft, List<DocumentSnapshot> snapshots, boolean advisoryEnabled, String retryOfRunId
    ) {
        DraftReviewInputs.Projection projection = reviewInputs.projection(draft.knowledgeBaseId(), draft.id());
        String projectionJson = jsonSupport.canonical(jsonSupport.parse(projection.schemaJson()));
        EvaluationProfiles.Profile profile = profiles.activeProfile(draft.knowledgeBaseId());
        String settingsJson = jsonSupport.canonical(Map.of("advisoryEnabled", advisoryEnabled));
        String documentSnapshotJson = jsonSupport.canonical(snapshots);
        String snapshotFingerprint = jsonSupport.fingerprint(projectionJson + documentSnapshotJson + profile.id()
            + profile.revision() + SchemaDraftEvaluationContracts.CONTRACT_REVISION + settingsJson);
        SchemaDraftEvaluationRunNode run = new SchemaDraftEvaluationRunNode();
        run.setId(UUID.randomUUID().toString());
        run.setDraftId(draft.id());
        run.setKnowledgeBaseId(draft.knowledgeBaseId());
        run.setStatus(SchemaDraftEvaluationStatus.QUEUED);
        run.setDraftRevision(draft.revision());
        run.setAggregateRevisionId(projection.aggregateRevisionId());
        run.setProjectionJson(projectionJson);
        run.setProjectionContentHash(jsonSupport.fingerprint(projectionJson));
        run.setGuidanceJson(draft.guidanceJson());
        String decisionsJson = reviewInputs.canonicalDecisions(draft.knowledgeBaseId(), draft.id());
        run.setDecisionsJson(decisionsJson);
        run.setDecisionsFingerprint(jsonSupport.fingerprint(decisionsJson));
        run.setDocumentSnapshotJson(documentSnapshotJson);
        run.setAiProfileId(profile.id());
        run.setAiProfileRevision(profile.revision());
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
            outcome.setDraftId(draft.id());
            outcome.setDocumentId(snapshot.documentId());
            outcome.setDocumentSha256(snapshot.sha256());
            outcome.setReuseKey(reuseKey(run, snapshot));
            outcome.setStatus(SchemaDraftEvaluationOutcomeStatus.QUEUED);
            outcome.setRetryable(true);
            outcomes.add(outcome);
        }
        SchemaDraftEvaluationRunNode saved = checkpointService.create(run, outcomes);
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
            draft.id(), saved.getId(), snapshots.size(), saved.getProjectionContentHash());
        return new StartEvaluationResponse(saved.getId(), saved.getStatus(), statusLocation(saved));
    }

    void execute(String runId) {
        String workerId = UUID.randomUUID().toString();
        Instant claimedAt = Instant.now();
        if (!Long.valueOf(1).equals(
            runRepository.claim(runId, workerId, claimedAt, claimedAt.plus(CLAIM_DURATION)))) return;
        SchemaDraftEvaluationRunNode run = runRepository.findById(runId).orElseThrow();
        long started = System.nanoTime();
        SchemaDocument schema = registry.parseAndValidate(run.getProjectionJson()).schema();
        boolean clientAvailable = extraction.available();
        Map<String, String> attributes = Map.of(
            "ai.draft.id", run.getDraftId(), "ai.draft.evaluation_run_id", runId,
            "ai.draft.document_count", Integer.toString(run.getTotalDocuments()),
            "ai.draft.projection_hash", run.getProjectionContentHash());
        try (AiObservationScope workflow = observationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_SCHEMA_DRAFT_EVALUATION, schema.name(), attributes))) {
            try {
                List<SchemaDraftEvaluationOutcomeNode> outcomes = outcomeRepository.findByRunIdOrderByDocumentIdAsc(runId);
                for (SchemaDraftEvaluationOutcomeNode outcome : outcomes) {
                    processOutcome(run, outcome, schema, clientAvailable);
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
        SchemaDocument schema, boolean clientAvailable
    ) {
        outcome.setStatus(SchemaDraftEvaluationOutcomeStatus.RUNNING);
        outcome.setStartedAt(Instant.now());
        SchemaDraftEvaluationOutcomeNode runningOutcome = outcomeRepository.save(outcome);
        EvaluationDocuments.Source source = documents.captureOwned(run.getKnowledgeBaseId(), runningOutcome.getDocumentId()).orElse(null);
        if (source == null || !runningOutcome.getDocumentSha256().equals(source.metadata().sha256())) {
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
        if (!clientAvailable) {
            completeOutcome(
                runningOutcome, SchemaDraftEvaluationOutcomeStatus.FAILED, "AI_CLIENT_UNAVAILABLE", true);
            return;
        }
        try {
            List<String> chunks = source.prepare().chunks();
            List<Metrics> metrics = new ArrayList<>();
            Set<String> coordinates = new LinkedHashSet<>();
            SupportRisk risk = supportRisk(run);
            for (String chunk : chunks) {
                Observation observation = extraction.extract(schema, chunk, run.getAiProfileId());
                metrics.add(metricsCalculator.calculate(observation.raw(), observation.validated(), schema,
                    risk.lowSupport(), risk.guidedWithoutEvidence()));
                observation.raw().nodes().forEach(node -> coordinates.add("node:" + node.label()));
                observation.raw().relationships().forEach(relationship -> coordinates.add(
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
        List<String> intended = reviewInputs.intendedQuestions(run.getGuidanceJson());
        for (String question : intended) {
            questions.add(new QuestionAssessment(jsonSupport.fingerprint(question), "UNASSESSED", List.of()));
        }
        SupportRisk risk = supportRisk(run);
        List<CoordinateAssessment> noise = risk.riskyCoordinates().stream()
            .map(value -> new CoordinateAssessment(value, "POSSIBLE_NOISE", "low support or guided without evidence"))
            .toList();
        return new AdvisoryAssessment("COMPLETED_WITHOUT_MODEL_JUDGMENT", questions, noise, run.getAiProfileId(),
            run.getAiProfileRevision(), run.getPromptRevision(), "No advisory model adapter is configured; deterministic results are unaffected");
    }

    private SupportRisk supportRisk(SchemaDraftEvaluationRunNode run) {
        JsonNode candidates = jsonSupport.parse(reviewInputs.aggregate(run.getKnowledgeBaseId(), run.getDraftId(), run.getAggregateRevisionId()).candidatesJson());
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
