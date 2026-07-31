package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAttemptNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchResultNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.CreateRequest;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.ResultResponse;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.RunResponse;
import io.github.vfedoriv.graphrag.dto.PageResponse;
import io.github.vfedoriv.graphrag.error.AdvancedSearchCapacityException;
import io.github.vfedoriv.graphrag.error.AdvancedSearchResultUnavailableException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.AdvancedSearchAttemptRepository;
import io.github.vfedoriv.graphrag.repository.AdvancedSearchResultRepository;
import io.github.vfedoriv.graphrag.repository.AdvancedSearchRunRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRunProcessor.Attempt;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRunProcessor.Context;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRunProcessor.ProcessingResult;
import io.github.vfedoriv.graphrag.service.DefaultAdvancedSearchRunProcessor.AdvancedSearchStoppedException;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService.AdvancedSearchSettings;
import io.github.vfedoriv.graphrag.observability.AdvancedSearchMetrics;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdvancedSearchRunService {
    private final AdvancedSearchRunRepository runRepository;
    private final AdvancedSearchAttemptRepository attemptRepository;
    private final AdvancedSearchResultRepository resultRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final RuntimeSettingsService settingsService;
    private final AdvancedSearchRunProcessor processor;
    private final AdvancedSearchRunLifecycle lifecycle;
    private final AdvancedSearchResultCodec resultCodec;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate transactions;
    private final AdvancedSearchAdmission admission;
    private final AdvancedSearchMetrics metrics;
    private final Map<String, FutureTask<Void>> futures = new ConcurrentHashMap<>();
    private final Map<String, AdvancedSearchAdmission.Reservation> permits = new ConcurrentHashMap<>();
    private final String workerId = UUID.randomUUID().toString();

    public AdvancedSearchRunService(
        AdvancedSearchRunRepository runRepository,
        AdvancedSearchAttemptRepository attemptRepository,
        AdvancedSearchResultRepository resultRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        SchemaDefinitionRepository schemaDefinitionRepository,
        RuntimeSettingsService settingsService,
        AdvancedSearchRunProcessor processor,
        AdvancedSearchRunLifecycle lifecycle,
        AdvancedSearchResultCodec resultCodec,
        ObjectMapper objectMapper,
        AdvancedSearchAdmission admission,
        AdvancedSearchMetrics metrics,
        @Qualifier("advancedSearchRunExecutor") ThreadPoolTaskExecutor executor,
        @Qualifier("transactionManager") PlatformTransactionManager transactionManager
    ) {
        this.runRepository = runRepository; this.attemptRepository = attemptRepository;
        this.resultRepository = resultRepository; this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.settingsService = settingsService; this.processor = processor; this.lifecycle = lifecycle;
        this.resultCodec = resultCodec; this.objectMapper = objectMapper; this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.admission = admission;
        this.metrics = metrics;
    }

    public RunResponse create(String knowledgeBaseId, CreateRequest request) {
        AdvancedSearchSettings settings = settingsService.advancedSearch();
        String query = request.query().strip();
        if (query.length() > settings.maxQueryLength()) {
            throw new IllegalArgumentException("query exceeds the configured advanced-search length bound");
        }
        int maximumEvidence = request.maximumEvidence() == null ? settings.defaultEvidence() : request.maximumEvidence();
        if (maximumEvidence > settings.maxEvidence()) {
            throw new IllegalArgumentException("maximumEvidence exceeds the configured advanced-search bound");
        }
        AdvancedSearchAdmission.Reservation reservation = admission.tryReserve()
            .orElseThrow(AdvancedSearchCapacityException::new);
        try {
            AdvancedSearchRunNode saved = transactions.execute(status -> {
                KnowledgeBaseNode knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                    .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
                Instant now = Instant.now();
                AdvancedSearchRunNode run = new AdvancedSearchRunNode();
                run.setId(UUID.randomUUID().toString()); run.setKnowledgeBaseId(knowledgeBaseId); run.setQueryText(query);
                run.setStatus(AdvancedSearchRunStatus.QUEUED); run.setStage(AdvancedSearchRunStage.QUEUED);
                run.setRequestedEvidence(maximumEvidence);
                run.setIncludeEvidenceText(Boolean.TRUE.equals(request.includeEvidenceText()));
                run.setSettingsSnapshotJson(snapshot(settings)); run.setCompletedBranches(0); run.setTotalBranches(3);
                run.setActiveAiProfileId(knowledgeBase.getActiveAiProfileId());
                snapshotSchema(run, knowledgeBase);
                run.setEvidenceCount(0); run.setDeadlineAt(now.plus(settings.deadline())); run.setCreatedAt(now);
                return runRepository.save(run);
            });
            permits.put(saved.getId(), reservation);
            dispatch(saved.getId(), settings);
            return toResponse(saved);
        } catch (RuntimeException exception) {
            reservation.release();
            throw exception;
        }
    }

    public PageResponse<RunResponse> list(
        String knowledgeBaseId, AdvancedSearchRunStatus status, int page, int size
    ) {
        requirePage(page, size);
        if (!knowledgeBaseRepository.existsById(knowledgeBaseId)) {
            throw new NotFoundException("Knowledge base not found: " + knowledgeBaseId);
        }
        Page<AdvancedSearchRunNode> result = runRepository.findOwnedPage(
            knowledgeBaseId, status, PageRequest.of(page, size));
        return new PageResponse<>(page, size, result.getTotalElements(), result.getContent().stream()
            .map(this::toResponse).toList());
    }

    public RunResponse get(String knowledgeBaseId, String runId) {
        return toResponse(requireOwned(knowledgeBaseId, runId));
    }

    public ResultResponse result(String knowledgeBaseId, String runId) {
        AdvancedSearchRunNode run = requireOwned(knowledgeBaseId, runId);
        AdvancedSearchResultNode result = resultRepository.findByRunId(runId).orElseThrow(
            () -> new AdvancedSearchResultUnavailableException(run.getStatus(), run.getStage()));
        return new ResultResponse(runId, result.getPayloadVersion(), resultCodec.read(result.getResultJson()), result.getCreatedAt());
    }

    public RunResponse cancel(String knowledgeBaseId, String runId) {
        CancellationOutcome outcome = transactions.execute(status -> {
            AdvancedSearchRunNode before = requireOwned(knowledgeBaseId, runId);
            if (before.getStatus().terminal()) { return new CancellationOutcome(before, false); }
            Instant now = Instant.now();
            boolean queued = runRepository.cancelQueued(runId, knowledgeBaseId, now,
                now.plus(settingsService.advancedSearch().retention()));
            if (!queued) { runRepository.requestRunningCancellation(runId, knowledgeBaseId, now); }
            return new CancellationOutcome(requireOwned(knowledgeBaseId, runId), queued);
        });
        FutureTask<Void> future = futures.get(runId);
        if (future != null && (outcome.queued() || outcome.run().getStatus() == AdvancedSearchRunStatus.RUNNING)) {
            future.cancel(true);
        }
        if (outcome.queued()) {
            futures.remove(runId);
            release(runId);
        }
        return toResponse(outcome.run());
    }

    private void dispatch(String runId, AdvancedSearchSettings settings) {
        FutureTask<Void> task = new FutureTask<>(() -> { work(runId, settings); return null; });
        futures.put(runId, task);
        try {
            executor.execute(task);
        } catch (RuntimeException exception) {
            futures.remove(runId);
            permits.remove(runId);
            throw new AdvancedSearchCapacityException();
        }
    }

    private void work(String runId, AdvancedSearchSettings settings) {
        try {
            boolean claimed = transactions.execute(status -> runRepository.claim(runId, workerId, Instant.now()));
            if (!claimed) {
                return;
            }
            AdvancedSearchRunNode claimedRun = runRepository.findById(runId).orElseThrow();
            ProcessingResult processed = processor.process(new Context(
                claimedRun.getKnowledgeBaseId(), claimedRun.getQueryText(), claimedRun.getActiveAiProfileId(),
                claimedRun.getSchemaDefinitionId(), claimedRun.getSchemaContentHash(), claimedRun.getSchemaSnapshotJson(),
                claimedRun.getRequestedEvidence(),
                claimedRun.isIncludeEvidenceText(), claimedRun.getDeadlineAt(), settings,
                () -> stopped(runId), stage -> updateStage(runId, stage)
            ));
            saveAttempts(runId, processed);
            transactions.executeWithoutResult(status -> complete(runId, processed, settings));
        } catch (AdvancedSearchStoppedException exception) {
            transactions.executeWithoutResult(status -> stop(runId, exception.getMessage(), settings));
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> fail(runId, exception.getClass().getSimpleName(), settings));
        } finally {
            futures.remove(runId);
            release(runId);
        }
    }

    private void complete(String runId, ProcessingResult processed, AdvancedSearchSettings settings) {
        AdvancedSearchRunNode run = runRepository.findById(runId).orElseThrow();
        if (lifecycle.shouldStop(run, Instant.now())) {
            stopNode(run, run.getCancellationRequestedAt() == null ? "DEADLINE_EXCEEDED" : "CANCELLED", settings);
            return;
        }
        String json = resultCodec.write(processed.payload(), processed.evidenceCount(), run.getRequestedEvidence());
        AdvancedSearchResultNode result = new AdvancedSearchResultNode(); result.setRunId(runId);
        result.setPayloadVersion(AdvancedSearchResultCodec.PAYLOAD_VERSION); result.setResultJson(json);
        result.setEvidenceCount(processed.evidenceCount()); result.setCreatedAt(Instant.now()); resultRepository.save(result);
        run.setCompletedBranches(processed.attempts().size());
        run.setTotalBranches(Math.max(1, processed.totalBranches()));
        AdvancedSearchRunStatus terminal = processed.answered()
            && processed.successfulBranches() == processed.totalBranches()
            ? AdvancedSearchRunStatus.COMPLETED : AdvancedSearchRunStatus.PARTIAL;
        String failureCategory = processed.answerFailureCategory() != null
            ? processed.answerFailureCategory()
            : terminal == AdvancedSearchRunStatus.PARTIAL ? "BRANCH_FAILURE" : null;
        lifecycle.terminal(run, terminal, failureCategory,
            processed.evidenceCount(), Instant.now(), settings.retention());
        runRepository.save(run);
        metrics.terminal(terminal, failureCategory);
    }

    private void saveAttempts(String runId, ProcessingResult processed) {
        Instant now = Instant.now();
        for (Attempt value : processed.attempts()) {
            AdvancedSearchAttemptNode attempt = new AdvancedSearchAttemptNode(); attempt.setId(UUID.randomUUID().toString());
            attempt.setRunId(runId); attempt.setRoundNumber(value.roundNumber()); attempt.setSubqueryId(value.subqueryId());
            attempt.setRetriever(value.retriever()); attempt.setStatus(value.status());
            attempt.setCandidateCount(value.candidateCount()); attempt.setLatencyMs(value.latencyMs());
            attempt.setFailureCategory(value.failureCategory());
            attempt.setCreatedAt(now); attempt.setCompletedAt(now); attemptRepository.save(attempt);
        }
    }

    private void updateStage(String runId, AdvancedSearchRunStage stage) {
        transactions.executeWithoutResult(status -> {
            AdvancedSearchRunNode run = runRepository.findById(runId).orElseThrow();
            if (!lifecycle.shouldStop(run, Instant.now())) {
                lifecycle.moveTo(run, stage); runRepository.save(run);
            }
        });
    }

    private boolean stopped(String runId) {
        return runRepository.findById(runId).map(run -> lifecycle.shouldStop(run, Instant.now())).orElse(true);
    }

    private void stop(String runId, String category, AdvancedSearchSettings settings) {
        runRepository.findById(runId).ifPresent(run -> {
            if (!run.getStatus().terminal()) { stopNode(run, category, settings); }
        });
    }
    private void stopNode(AdvancedSearchRunNode run, String category, AdvancedSearchSettings settings) {
        AdvancedSearchRunStatus status = "CANCELLED".equals(category)
            ? AdvancedSearchRunStatus.CANCELLED : AdvancedSearchRunStatus.FAILED;
        lifecycle.terminal(run, status, category, 0, Instant.now(), settings.retention()); runRepository.save(run);
        metrics.terminal(status, category);
    }
    private void fail(String runId, String category, AdvancedSearchSettings settings) {
        runRepository.findById(runId).ifPresent(run -> {
            if (!run.getStatus().terminal()) {
                lifecycle.terminal(run, AdvancedSearchRunStatus.FAILED, category, 0, Instant.now(), settings.retention());
                runRepository.save(run);
                metrics.terminal(AdvancedSearchRunStatus.FAILED, category);
            }
        });
    }

    private AdvancedSearchRunNode requireOwned(String knowledgeBaseId, String runId) {
        return runRepository.findOwned(runId, knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Advanced search run not found: " + runId));
    }
    private String snapshot(AdvancedSearchSettings settings) {
        try { return objectMapper.writeValueAsString(settings); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot snapshot advanced-search settings", exception); }
    }
    private void snapshotSchema(AdvancedSearchRunNode run, KnowledgeBaseNode knowledgeBase) {
        if (knowledgeBase.getActiveSchemaId() == null || knowledgeBase.getActiveSchemaId().isBlank()) {
            return;
        }
        SchemaDefinitionNode schema = schemaDefinitionRepository.findById(knowledgeBase.getActiveSchemaId())
            .orElseThrow(() -> new NotFoundException("Schema not found: " + knowledgeBase.getActiveSchemaId()));
        run.setSchemaDefinitionId(schema.getId());
        run.setSchemaContentHash(schema.getContentHash());
        run.setSchemaSnapshotJson(schema.getContent());
    }
    private void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) { throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100"); }
    }
    private RunResponse toResponse(AdvancedSearchRunNode run) {
        String base = "/api/v1/knowledge-bases/" + run.getKnowledgeBaseId() + "/queries/advanced-search-runs/" + run.getId();
        return new RunResponse(run.getId(), run.getKnowledgeBaseId(), run.getStatus(), run.getStage(),
            run.getCompletedBranches(), run.getTotalBranches(), run.getEvidenceCount(),
            run.getCancellationRequestedAt() != null, run.getFailureCategory(), run.getDeadlineAt(), run.getCreatedAt(),
            run.getStartedAt(), run.getCompletedAt(), Map.of("self", base, "result", base + "/result", "cancel", base + "/cancel"));
    }

    private void release(String runId) {
        AdvancedSearchAdmission.Reservation permit = permits.remove(runId);
        if (permit != null) { permit.release(); }
    }

    private record CancellationOutcome(AdvancedSearchRunNode run, boolean queued) { }
}
