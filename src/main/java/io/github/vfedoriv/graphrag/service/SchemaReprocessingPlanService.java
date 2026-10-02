package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftJsonSupport;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftWorkflowCheckpointService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftWorkflowNavigationService;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.CreatePlanRequest;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationBlocker;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationClassificationCounts;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationDocumentPreview;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationDocumentPreviewPage;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationPreviewRequest;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationPreviewResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationTarget;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanItemResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanItemPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanSummaryResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.StartPlanResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.RetryMode;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.RetryPlanRequest;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.github.vfedoriv.graphrag.schemas.reprocessing.application.ReprocessingItemExecution;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentExecutor;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentPreparation;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentPreparation.Summary;

@Service
@Slf4j
public class SchemaReprocessingPlanService {
    private static final Duration CLAIM_DURATION = Duration.ofMinutes(30);
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final SchemaDraftPublicationRepository publicationRepository;
    private final SchemaReprocessingPlanRepository planRepository;
    private final SchemaReprocessingItemRepository itemRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ReprocessingItemExecution itemExecution;
    private final ReprocessingDocumentPreparation preparation;
    private final SchemaDraftJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;
    private final AiObservationService observationService;
    private final SchemaDraftLifecycleService draftLifecycleService;
    private final SchemaDraftWorkflowNavigationService workflowNavigationService;
    private final SchemaDraftWorkflowCheckpointService checkpointService;

    public SchemaReprocessingPlanService(
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        KnowledgeBaseRepository knowledgeBaseRepository,
        ReprocessingDocumentPreparation preparation,
        SchemaDefinitionRepository schemaRepository,
        SchemaDraftPublicationRepository publicationRepository,
        SchemaReprocessingPlanRepository planRepository,
        SchemaReprocessingItemRepository itemRepository,
        KnowledgeBaseService knowledgeBaseService,
        ReprocessingItemExecution itemExecution,
        SchemaDraftJsonSupport jsonSupport,
        ObjectMapper objectMapper,
        AiObservationService observationService,
        SchemaDraftLifecycleService draftLifecycleService,
        SchemaDraftWorkflowNavigationService workflowNavigationService,
        SchemaDraftWorkflowCheckpointService checkpointService,
        @Qualifier("schemaReprocessingExecutor") TaskExecutor executor
    ) {
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.preparation = preparation;
        this.schemaRepository = schemaRepository;
        this.publicationRepository = publicationRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.itemExecution = itemExecution;
        this.jsonSupport = jsonSupport;
        this.objectMapper = objectMapper;
        this.observationService = observationService;
        this.draftLifecycleService = draftLifecycleService;
        this.workflowNavigationService = workflowNavigationService;
        this.checkpointService = checkpointService;
        this.executor = executor;
    }

    @RelationalTransactional
    public StartPlanResponse create(String knowledgeBaseId, CreatePlanRequest request) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        ReprocessingPlanReason reason = request.reason() == null
            ? ReprocessingPlanReason.SCHEMA_ACTIVATION
            : request.reason();
        if (reason == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION) {
            return createChunkPlan(knowledgeBaseId, request, null, Map.of());
        }
        return createSchemaPlan(knowledgeBaseId, request, null, Map.of());
    }

    @RelationalTransactional(readOnly = true)
    public ChunkMigrationPreviewResponse preview(
        String knowledgeBaseId, ChunkMigrationPreviewRequest request, int page, int size
    ) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        if (request == null || request.selection() == null) {
            throw new IllegalArgumentException("Chunk migration preview requires selection");
        }
        ChunkMigrationEvaluation evaluation = evaluateChunkMigration(
            knowledgeBaseId,
            request.selection(),
            request.documentIds(),
            request.processingOptions()
        );
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        List<ChunkMigrationDocumentPreview> selected = evaluation.selectedDocuments().stream()
            .map(document -> new ChunkMigrationDocumentPreview(
                document.id(), document.originalFilename(), document.sha256(), document.uploadedAt(),
                evaluation.classifications().get(document.id()).classification(),
                evaluation.documentTargets().get(document.id()).effectiveChunkerRevision(),
                evaluation.documentTargets().get(document.id()).parserRevision()
            ))
            .toList();
        int from = Math.min(selected.size(), boundedPage * boundedSize);
        int to = Math.min(selected.size(), from + boundedSize);
        ChunkMigrationDocumentPreviewPage pageResponse = new ChunkMigrationDocumentPreviewPage(
            boundedPage, boundedSize, selected.size(), selected.subList(from, to)
        );
        ChunkMigrationTarget target = evaluation.target() == null ? null : new ChunkMigrationTarget(
            evaluation.target().schemaId(), evaluation.target().schemaContentHash(),
            evaluation.target().aiProfileId(), evaluation.target().aiProfileRevision(),
            evaluation.target().embeddingSpaceId(), evaluation.target().expectedChunkerRevision()
        );
        return new ChunkMigrationPreviewResponse(
            knowledgeBaseId, request.selection(), evaluation.ready(), evaluation.blockers(), target,
            new ChunkMigrationClassificationCounts(
                evaluation.noChunks(), evaluation.outdated(), evaluation.current()
            ),
            selected.size(), pageResponse
        );
    }

    private StartPlanResponse createSchemaPlan(
        String knowledgeBaseId,
        CreatePlanRequest request,
        String retryOfPlanId,
        Map<String, String> priorItemIds
    ) {
        requireNonBlank(request.draftId(), "draftId");
        requireNonBlank(request.schemaId(), "schemaId");
        SchemaDraftPublicationNode publication = publicationRepository.findByDraftId(request.draftId())
            .filter(value -> knowledgeBaseId.equals(value.getKnowledgeBaseId()) && request.schemaId().equals(value.getSchemaId()))
            .orElseThrow(() -> new NotFoundException("Published draft schema not found in knowledge base"));
        SchemaDefinitionNode schema = requireActiveTarget(knowledgeBaseId, publication.getSchemaId());
        List<Summary> documents = selectedDocuments(knowledgeBaseId, request);
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setId(UUID.randomUUID().toString());
        plan.setReason(ReprocessingPlanReason.SCHEMA_ACTIVATION);
        plan.setDraftId(request.draftId());
        plan.setKnowledgeBaseId(knowledgeBaseId);
        plan.setSchemaId(schema.getId());
        plan.setSchemaContentHash(schema.getContentHash());
        plan.setAiProfileId(profile.getId());
        plan.setAiProfileRevision(profile.getRevision());
        plan.setProcessingOptionsJson(jsonSupport.canonical(request.processingOptions() == null ? Map.of() : request.processingOptions()));
        plan.setRetryOfPlanId(retryOfPlanId);
        plan.setStatus(SchemaReprocessingPlanStatus.QUEUED);
        plan.setTotalDocuments(documents.size());
        plan.setQueuedDocuments(documents.size());
        plan.setCreatedAt(Instant.now());
        List<SchemaReprocessingItemNode> items = new ArrayList<>();
        for (Summary document : documents) {
            items.add(createItem(plan.getId(), document, priorItemIds.get(document.id())));
        }
        SchemaReprocessingPlanNode saved = checkpointService.createPlan(plan, items);
        scheduleAfterCommit(saved);
        log.info("Schema reprocessing plan queued: planId={}, knowledgeBaseId={}, schemaId={}, documentCount={}, schemaHash={}",
            saved.getId(), knowledgeBaseId, schema.getId(), documents.size(), schema.getContentHash());
        return new StartPlanResponse(saved.getId(), saved.getStatus(), statusLocation(saved));
    }

    private StartPlanResponse createChunkPlan(
        String knowledgeBaseId,
        CreatePlanRequest request,
        String retryOfPlanId,
        Map<String, String> priorItemIds
    ) {
        ChunkReprocessingSelection selection = request.selection();
        if (selection == null) {
            throw new IllegalArgumentException("Chunk strategy migration requires selection");
        }
        requireNonBlank(request.expectedChunkerRevision(), "expectedChunkerRevision");
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        String targetRevision = preparation.targetRevision(capturedProfile(profile));
        if (!targetRevision.equals(request.expectedChunkerRevision())) {
            throw new ConflictException(
                "Expected chunker revision is stale; current revision is " + targetRevision
            );
        }
        ChunkMigrationEvaluation evaluation = evaluateChunkMigration(
            knowledgeBaseId, selection, request.documentIds(), request.processingOptions()
        );
        if (!evaluation.ready()) {
            throw new ConflictException(evaluation.blockers().get(0).message());
        }
        if (evaluation.target() == null
            || !targetRevision.equals(evaluation.target().expectedChunkerRevision())) {
            throw new ConflictException(
                "Expected chunker revision is stale; current revision is "
                    + (evaluation.target() == null ? "unavailable" : evaluation.target().expectedChunkerRevision())
            );
        }
        KnowledgeBaseNode knowledgeBase = evaluation.knowledgeBase();
        SchemaDefinitionNode schema = evaluation.schema();
        String embeddingSpace = evaluation.embeddingSpace();
        ChunkMigrationSnapshot.ChunkTarget chunkTarget = evaluation.chunkTarget();
        Map<String, ChunkMigrationSnapshot.DocumentTarget> documentTargets = evaluation.documentTargets();
        List<Summary> documents = evaluation.selectedDocuments();
        ChunkMigrationSnapshot snapshot = new ChunkMigrationSnapshot(
            targetRevision,
            selection,
            chunkTarget,
            profile.getId(),
            profile.getRevision(),
            embeddingSpace,
            schema.getId(),
            schema.getContentHash(),
            documentTargets
        );
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setId(UUID.randomUUID().toString());
        plan.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        plan.setSelection(selection);
        plan.setExpectedChunkerRevision(targetRevision);
        plan.setTargetSnapshotJson(jsonSupport.canonical(snapshot));
        plan.setEmbeddingSpaceId(embeddingSpace);
        plan.setKnowledgeBaseId(knowledgeBaseId);
        plan.setSchemaId(schema.getId());
        plan.setSchemaContentHash(schema.getContentHash());
        plan.setAiProfileId(profile.getId());
        plan.setAiProfileRevision(profile.getRevision());
        plan.setProcessingOptionsJson("{}");
        plan.setRetryOfPlanId(retryOfPlanId);
        plan.setStatus(SchemaReprocessingPlanStatus.QUEUED);
        plan.setTotalDocuments(documents.size());
        plan.setQueuedDocuments(documents.size());
        plan.setCreatedAt(Instant.now());
        List<SchemaReprocessingItemNode> items = new ArrayList<>();
        for (Summary document : documents) {
            items.add(createItem(plan.getId(), document, priorItemIds.get(document.id())));
        }
        SchemaReprocessingPlanNode saved = checkpointService.createPlan(plan, items);
        scheduleAfterCommit(saved);
        log.info(
            "Chunk strategy migration plan queued: planId={}, knowledgeBaseId={}, documentCount={}, targetRevision={}",
            saved.getId(),
            knowledgeBaseId,
            documents.size(),
            targetRevision
        );
        return new StartPlanResponse(saved.getId(), saved.getStatus(), statusLocation(saved));
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void createForActivation(String knowledgeBaseId, String schemaId) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        publicationRepository.findBySchemaId(schemaId)
            .filter(publication -> knowledgeBaseId.equals(publication.getKnowledgeBaseId()))
            .ifPresent(publication -> createSchemaPlan(
                knowledgeBaseId,
                new CreatePlanRequest(publication.getDraftId(), schemaId, true, List.of(), Map.of()),
                null,
                Map.of()
            ));
    }

    @RelationalTransactional
    public StartPlanResponse retry(String knowledgeBaseId, String planId, boolean resnapshot) {
        if (!resnapshot) throw new IllegalArgumentException("Retry requires explicit unresolved-document resnapshot");
        return retryInternal(knowledgeBaseId, planId, RetryMode.RESNAPSHOT_UNRESOLVED);
    }

    @RelationalTransactional
    public StartPlanResponse retry(String knowledgeBaseId, String planId, RetryPlanRequest request) {
        RetryMode mode = resolveRetryMode(request);
        return retryInternal(knowledgeBaseId, planId, mode);
    }

    private StartPlanResponse retryInternal(String knowledgeBaseId, String planId, RetryMode mode) {
        if (mode != RetryMode.RESNAPSHOT_UNRESOLVED) {
            throw new IllegalArgumentException("Unsupported retry mode: " + mode);
        }
        SchemaReprocessingPlanNode prior = requirePlan(knowledgeBaseId, planId);
        if (prior.getStatus() == SchemaReprocessingPlanStatus.QUEUED || prior.getStatus() == SchemaReprocessingPlanStatus.RUNNING) {
            throw new ConflictException("Reprocessing plan is still running: " + planId);
        }
        List<SchemaReprocessingItemNode> unresolved = itemRepository.findByPlanIdOrderByDocumentIdAsc(planId).stream()
            .filter(value -> value.getStatus() != SchemaReprocessingItemStatus.SUCCEEDED).toList();
        if (unresolved.isEmpty()) {
            throw new ConflictException("Reprocessing plan has no unresolved documents: " + planId);
        }
        List<String> unresolvedDocumentIds = new ArrayList<>();
        Map<String, String> priorItemIds = new LinkedHashMap<>();
        for (SchemaReprocessingItemNode priorItem : unresolved) {
            unresolvedDocumentIds.add(priorItem.getDocumentId());
            priorItemIds.put(priorItem.getDocumentId(), priorItem.getId());
        }
        if (prior.getReason() == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION) {
            AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
            CreatePlanRequest retryRequest = new CreatePlanRequest(
                null,
                null,
                false,
                unresolvedDocumentIds,
                Map.of(),
                ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION,
                ChunkReprocessingSelection.DOCUMENT_IDS,
                preparation.targetRevision(capturedProfile(profile))
            );
            return createChunkPlan(knowledgeBaseId, retryRequest, prior.getId(), priorItemIds);
        }
        Map<String, Object> processingOptions = objectMapper.convertValue(
            jsonSupport.parse(prior.getProcessingOptionsJson()),
            new TypeReference<Map<String, Object>>() { }
        );
        return createSchemaPlan(
            knowledgeBaseId,
            new CreatePlanRequest(
                prior.getDraftId(),
                prior.getSchemaId(),
                false,
                unresolvedDocumentIds,
                processingOptions
            ),
            prior.getId(),
            priorItemIds
        );
    }

    private RetryMode resolveRetryMode(RetryPlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Retry request must not be null");
        }
        if (request.mode() != null && request.resnapshotUnresolvedDocuments() != null) {
            throw new IllegalArgumentException("Retry mode conflicts with deprecated resnapshot boolean");
        }
        if (request.mode() != null) {
            return request.mode();
        }
        if (Boolean.TRUE.equals(request.resnapshotUnresolvedDocuments())) {
            return RetryMode.RESNAPSHOT_UNRESOLVED;
        }
        throw new IllegalArgumentException("Retry requires mode=RESNAPSHOT_UNRESOLVED");
    }

    @RelationalTransactional(readOnly = true)
    public PlanResponse get(String knowledgeBaseId, String planId, int page, int size) {
        SchemaReprocessingPlanNode plan = requirePlan(knowledgeBaseId, planId);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<SchemaReprocessingItemNode> items = itemRepository.findByPlanIdOrderByDocumentIdAsc(
            planId, PageRequest.of(boundedPage, boundedSize));
        return toResponse(plan, boundedPage, boundedSize, items.getContent(), items.getTotalElements());
    }

    private PlanPageResponse listInternal(
        String knowledgeBaseId,
        String draftId,
        ReprocessingPlanReason reason,
        ChunkReprocessingSelection selection,
        SchemaReprocessingPlanStatus status,
        int page,
        int size
    ) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        if (draftId != null && !draftId.isBlank()) {
            draftLifecycleService.requireOwned(knowledgeBaseId, draftId);
        }
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<SchemaReprocessingPlanNode> plans = planRepository.findPageByFilters(
            knowledgeBaseId,
            draftId == null || draftId.isBlank() ? null : draftId,
            reason,
            selection,
            status,
            PageRequest.of(boundedPage, boundedSize)
        );
        List<String> draftIds = plans.getContent().stream()
            .map(SchemaReprocessingPlanNode::getDraftId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        Set<String> latestIds = draftIds.isEmpty() ? Set.of() : planRepository.findLatestForDraftIds(draftIds).stream()
            .map(SchemaReprocessingPlanNode::getId).collect(Collectors.toSet());
        Map<String, Boolean> targetCurrent = plans.getContent().stream().collect(Collectors.toMap(
            SchemaReprocessingPlanNode::getId,
            plan -> plan.getReason() == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION
                ? targetStillActive(plan)
                : workflowNavigationService.targetCurrent(plan)
        ));
        List<PlanSummaryResponse> content = plans.getContent().stream()
            .map(plan -> toSummary(
                plan, latestIds.contains(plan.getId()), targetCurrent.getOrDefault(plan.getId(), false))).toList();
        return new PlanPageResponse(boundedPage, boundedSize, plans.getTotalElements(), content);
    }

    @RelationalTransactional(readOnly = true)
    public PlanPageResponse list(
        String knowledgeBaseId,
        String draftId,
        ReprocessingPlanReason reason,
        ChunkReprocessingSelection selection,
        SchemaReprocessingPlanStatus status,
        int page,
        int size
    ) {
        return listInternal(knowledgeBaseId, draftId, reason, selection, status, page, size);
    }

    @RelationalTransactional(readOnly = true)
    public PlanPageResponse list(String knowledgeBaseId, String draftId, int page, int size) {
        return listInternal(knowledgeBaseId, draftId, null, null, null, page, size);
    }

    void execute(String planId) {
        String workerId = UUID.randomUUID().toString();
        Instant claimedAt = Instant.now();
        if (!Long.valueOf(1).equals(
            planRepository.claim(planId, workerId, claimedAt, claimedAt.plus(CLAIM_DURATION)))) return;
        SchemaReprocessingPlanNode plan = planRepository.findById(planId).orElseThrow();
        long started = System.nanoTime();
        Map<String, String> attributes = Map.of(
            "ai.reprocessing.plan_id", planId, "knowledge_base.id", plan.getKnowledgeBaseId(),
            "schema.id", plan.getSchemaId(), "document.count", Integer.toString(plan.getTotalDocuments()));
        try (AiObservationScope workflow = observationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_SCHEMA_REPROCESSING, null, attributes))) {
            try {
                for (SchemaReprocessingItemNode item : itemRepository.findByPlanIdOrderByDocumentIdAsc(planId)) {
                    if (!targetStillActive(plan)) {
                        blockRemaining(plan);
                        break;
                    }
                    processItem(plan, item, workerId);
                    aggregate(plan);
                }
                aggregate(plan);
                workflow.success();
            } catch (RuntimeException exception) {
                workflow.error(exception);
                throw exception;
            }
        }
        log.info("Schema reprocessing plan completed: planId={}, knowledgeBaseId={}, status={}, succeeded={}, failed={}, stale={}, blocked={}, elapsedMs={}",
            plan.getId(), plan.getKnowledgeBaseId(), plan.getStatus(), plan.getSucceededDocuments(),
            plan.getFailedDocuments(), plan.getStaleDocuments(), plan.getBlockedDocuments(), LogMetadata.elapsedMillis(started));
    }

    private void processItem(
        SchemaReprocessingPlanNode plan, SchemaReprocessingItemNode item, String workerId
    ) {
        Instant claimedAt = Instant.now();
        if (!Long.valueOf(1).equals(itemRepository.claim(
            item.getId(), workerId, claimedAt, claimedAt.plus(CLAIM_DURATION)))) {
            return;
        }
        SchemaReprocessingItemNode claimedItem = itemRepository.findById(item.getId()).orElseThrow();
        if (!itemExecution.sourceMatches(new ReprocessingDocumentExecutor.Source(
            plan.getKnowledgeBaseId(), claimedItem.getDocumentId(), claimedItem.getDocumentSha256()))) {
            completeItem(claimedItem, workerId, SchemaReprocessingItemStatus.STALE_SOURCE, "SOURCE_CHANGED", false);
            return;
        }
        try {
            ReprocessingDocumentExecutor.Result result = itemExecution.execute(
                new ReprocessingDocumentExecutor.Request(
                    plan.getKnowledgeBaseId(), claimedItem.getDocumentId(), claimedItem.getDocumentSha256(),
                    plan.getAiProfileId(), executionTarget(plan, claimedItem)));
            SchemaReprocessingItemStatus status = switch (result.status()) {
                case STALE_SOURCE -> SchemaReprocessingItemStatus.STALE_SOURCE;
                case SUCCEEDED -> SchemaReprocessingItemStatus.SUCCEEDED;
                case FAILED -> SchemaReprocessingItemStatus.FAILED;
            };
            completeItem(claimedItem, workerId, status, result.failureCategory(),
                result.status() == ReprocessingDocumentExecutor.Status.FAILED);
            if (result.status() == ReprocessingDocumentExecutor.Status.FAILED
                && !"DOCUMENT_PROCESSING_FAILED".equals(result.failureCategory())) {
                log.warn("Schema reprocessing item failed: planId={}, documentId={}, exceptionType={}",
                    plan.getId(), claimedItem.getDocumentId(), result.failureCategory());
            }
        } catch (Exception exception) {
            completeItem(
                claimedItem, workerId, SchemaReprocessingItemStatus.FAILED,
                exception.getClass().getSimpleName(), true);
            log.warn("Schema reprocessing item failed: planId={}, documentId={}, exceptionType={}",
                plan.getId(), claimedItem.getDocumentId(), LogMetadata.exceptionType(exception));
        }
    }

    private ReprocessingDocumentExecutor.Target executionTarget(
        SchemaReprocessingPlanNode plan, SchemaReprocessingItemNode item
    ) throws java.io.IOException {
        if (plan.getReason() != ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION) {
            Map<String, Object> options = objectMapper.readValue(
                plan.getProcessingOptionsJson(), new TypeReference<Map<String, Object>>() { });
            return new ReprocessingDocumentExecutor.Activation(options);
        }
        ChunkMigrationSnapshot snapshot = jsonSupport.read(plan.getTargetSnapshotJson(), ChunkMigrationSnapshot.class);
        ChunkMigrationSnapshot.DocumentTarget document = snapshot.documents().get(item.getDocumentId());
        if (document == null) {
            throw new IllegalStateException("Migration snapshot is missing a selected document");
        }
        ChunkMigrationSnapshot.ChunkTarget chunk = snapshot.chunkTarget();
        return new ReprocessingDocumentExecutor.Migration(
            snapshot.aiProfileId(), snapshot.aiProfileRevision(), snapshot.embeddingSpaceId(),
            snapshot.schemaId(), snapshot.schemaContentHash(),
            new ReprocessingDocumentExecutor.ChunkTarget(
                chunk.strategyName(), chunk.strategyRevision(), chunk.targetTokens(), chunk.overlapTokens(),
                chunk.hardCharacterLimit(), chunk.parentTargetTokens(), chunk.parentHardCharacterLimit(),
                chunk.parentMaxPages(), chunk.contextHeaderMaxTokens(), chunk.contextHeaderMaxCharacters(),
                chunk.tokenizerId(), chunk.tokenizerRevision(), chunk.tokenCountMode(),
                chunk.representationRevision(), chunk.settingsHash()),
            new ReprocessingDocumentExecutor.DocumentTarget(
                document.sourceSha256(), document.parserId(), document.parserRevision(), document.fileFormat(),
                document.effectiveChunkerRevision(), document.effectiveProcessingOptions()));
    }

    private void aggregate(SchemaReprocessingPlanNode plan) {
        List<SchemaReprocessingItemNode> items = itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId());
        int queued = count(items, SchemaReprocessingItemStatus.QUEUED);
        int running = count(items, SchemaReprocessingItemStatus.RUNNING);
        int succeeded = count(items, SchemaReprocessingItemStatus.SUCCEEDED);
        int failed = count(items, SchemaReprocessingItemStatus.FAILED) + count(items, SchemaReprocessingItemStatus.INTERRUPTED);
        int stale = count(items, SchemaReprocessingItemStatus.STALE_SOURCE);
        int blocked = count(items, SchemaReprocessingItemStatus.BLOCKED)
            + count(items, SchemaReprocessingItemStatus.BLOCKED_TARGET_CHANGED)
            + count(items, SchemaReprocessingItemStatus.SKIPPED);
        plan.setQueuedDocuments(queued);
        plan.setRunningDocuments(running);
        plan.setSucceededDocuments(succeeded);
        plan.setFailedDocuments(failed);
        plan.setStaleDocuments(stale);
        plan.setBlockedDocuments(blocked);
        if (queued == 0 && running == 0) {
            plan.setStatus(succeeded == items.size() ? SchemaReprocessingPlanStatus.COMPLETED
                : succeeded > 0 ? SchemaReprocessingPlanStatus.PARTIAL : SchemaReprocessingPlanStatus.FAILED);
            plan.setCompletedAt(Instant.now());
        }
        SchemaReprocessingPlanNode savedPlan = planRepository.save(plan);
        plan.setPersistenceVersion(savedPlan.getPersistenceVersion());
    }

    private void blockRemaining(SchemaReprocessingPlanNode plan) {
        for (SchemaReprocessingItemNode item : itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId())) {
            if (item.getStatus() == SchemaReprocessingItemStatus.QUEUED) {
                item.setStatus(plan.getReason() == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION
                    ? SchemaReprocessingItemStatus.BLOCKED_TARGET_CHANGED
                    : SchemaReprocessingItemStatus.BLOCKED);
                item.setFailureCategory(plan.getReason() == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION
                    ? "TARGET_CHANGED"
                    : "ACTIVE_SCHEMA_CHANGED");
                item.setRetryable(true);
                item.setCompletedAt(Instant.now());
                itemRepository.save(item);
            }
        }
    }

    private boolean targetStillActive(SchemaReprocessingPlanNode plan) {
        KnowledgeBaseNode knowledgeBase = knowledgeBaseRepository.findById(plan.getKnowledgeBaseId()).orElse(null);
        SchemaDefinitionNode schema = schemaRepository.findById(plan.getSchemaId()).orElse(null);
        boolean schemaCurrent = knowledgeBase != null && schema != null
            && plan.getSchemaId().equals(knowledgeBase.getActiveSchemaId())
            && plan.getSchemaContentHash().equals(schema.getContentHash());
        if (!schemaCurrent || plan.getReason() != ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION) {
            return schemaCurrent;
        }
        try {
            AiProfileNode profile = knowledgeBaseService.activeAiProfile(plan.getKnowledgeBaseId());
            return plan.getAiProfileId().equals(profile.getId())
                && plan.getAiProfileRevision() == profile.getRevision()
                && documentTargetStillActive(plan, profile);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private SchemaDefinitionNode requireActiveTarget(String knowledgeBaseId, String schemaId) {
        KnowledgeBaseNode knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
        if (!schemaId.equals(knowledgeBase.getActiveSchemaId())) {
            throw new ConflictException("Published schema is not active for the knowledge base: " + schemaId);
        }
        return schemaRepository.findById(schemaId)
            .orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
    }

    private List<Summary> selectedDocuments(String knowledgeBaseId, CreatePlanRequest request) {
        boolean allDocuments = Boolean.TRUE.equals(request.allDocuments());
        boolean hasExplicitDocuments = request.documentIds() != null && !request.documentIds().isEmpty();
        if (allDocuments == hasExplicitDocuments) {
            throw new IllegalArgumentException("Select either allDocuments or an explicit non-empty documentIds list");
        }
        if (allDocuments) return preparation.allOwned(knowledgeBaseId);
        return preparation.ownedIds(knowledgeBaseId, request.documentIds());
    }

    private ChunkMigrationEvaluation evaluateChunkMigration(
        String knowledgeBaseId,
        ChunkReprocessingSelection selection,
        List<String> documentIds,
        Map<String, Object> processingOptions
    ) {
        Map<String, Object> options = processingOptions == null ? Map.of() : processingOptions;
        CreatePlanRequest request = new CreatePlanRequest(
            null, null, false, documentIds, options,
            ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION, selection, null
        );
        List<Summary> candidates = chunkCandidates(knowledgeBaseId, request, selection);
        List<Summary> allDocuments = preparation.allOwned(knowledgeBaseId);
        KnowledgeBaseNode knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
        List<ChunkMigrationBlocker> blockers = new ArrayList<>();
        if (knowledgeBase == null) {
            throw new NotFoundException("Knowledge base not found: " + knowledgeBaseId);
        }
        String activeSchemaId = knowledgeBase.getActiveSchemaId();
        SchemaDefinitionNode schema = null;
        if (activeSchemaId == null || activeSchemaId.isBlank()) {
            blockers.add(new ChunkMigrationBlocker(
                "ACTIVE_SCHEMA_MISSING", "Chunk strategy migration requires an active schema"
            ));
        } else {
            schema = schemaRepository.findById(activeSchemaId).orElse(null);
            if (schema == null) {
                blockers.add(new ChunkMigrationBlocker(
                    "ACTIVE_SCHEMA_MISSING", "The active schema could not be resolved"
                ));
            }
        }

        AiProfileNode profile = null;
        try {
            profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        } catch (RuntimeException exception) {
            blockers.add(new ChunkMigrationBlocker(
                "AI_PROFILE_UNRESOLVABLE", "The active AI profile could not be resolved"
            ));
        }
        String targetRevision = null;
        String embeddingSpace = null;
        ChunkMigrationSnapshot.ChunkTarget chunkTarget = null;
        if (profile != null) {
            ReprocessingDocumentPreparation.Inspection inspection = preparation.inspect(
                knowledgeBaseId, capturedProfile(profile));
            targetRevision = inspection.targetRevision();
            embeddingSpace = inspection.embeddingSpaceId();
            chunkTarget = savedChunkTarget(inspection.chunkTarget());
            if (inspection.blocker() != null) {
                blockers.add(new ChunkMigrationBlocker(inspection.blocker().code(), inspection.blocker().message()));
            }
        }

        if (planRepository.existsActiveByKnowledgeBaseId(knowledgeBaseId)) {
            blockers.add(new ChunkMigrationBlocker(
                "ACTIVE_DESTRUCTIVE_PLAN", "Another destructive reprocessing plan is active"
            ));
        }

        MigrationTarget target = schema == null || profile == null || embeddingSpace == null || targetRevision == null
            ? null
            : new MigrationTarget(
                schema.getId(), schema.getContentHash(), profile.getId(), profile.getRevision(),
                embeddingSpace, targetRevision
            );
        boolean canClassify = target != null && chunkTarget != null
            && blockers.stream().noneMatch(value -> value.code().equals("INVALID_MIGRATION_TARGET")
                || value.code().equals("EMBEDDING_SPACE_INCOMPATIBLE"));
        Map<String, ChunkMigrationDocumentClassification> classifications = new LinkedHashMap<>();
        Map<String, ChunkMigrationSnapshot.DocumentTarget> documentTargets = new LinkedHashMap<>();
        List<Summary> selected = new ArrayList<>();
        long noChunks = 0;
        long outdated = 0;
        long current = 0;
        if (canClassify) {
            for (ReprocessingDocumentPreparation.Prepared prepared : preparation.prepare(
                allDocuments, capturedProfile(profile), options)) {
                ReprocessingDocumentExecutor.DocumentTarget value = prepared.target();
                ChunkMigrationSnapshot.DocumentTarget documentTarget = new ChunkMigrationSnapshot.DocumentTarget(
                    value.sourceSha256(), value.parserId(), value.parserRevision(), value.fileFormat(),
                    value.effectiveChunkerRevision(), value.effectiveProcessingOptions());
                String classification = prepared.classification().name();
                classifications.put(prepared.document().id(), new ChunkMigrationDocumentClassification(
                    classification, documentTarget));
                switch (prepared.classification()) {
                    case NO_CHUNKS -> noChunks++;
                    case OUTDATED -> outdated++;
                    case CURRENT -> current++;
                }
            }
            for (Summary candidate : candidates) {
                ChunkMigrationDocumentClassification classification = classifications.get(candidate.id());
                boolean include = selection != ChunkReprocessingSelection.OUTDATED_STRATEGY
                    || "OUTDATED".equals(classification.classification())
                    || "NO_CHUNKS".equals(classification.classification());
                if (include) {
                    selected.add(candidate);
                    documentTargets.put(candidate.id(), classification.target());
                }
            }
        }
        return new ChunkMigrationEvaluation(
            knowledgeBase, schema, profile, embeddingSpace, chunkTarget, target, List.copyOf(blockers),
            selected, documentTargets, classifications, noChunks, outdated, current
        );
    }

    private List<Summary> chunkCandidates(
        String knowledgeBaseId,
        CreatePlanRequest request,
        ChunkReprocessingSelection selection
    ) {
        if (selection == ChunkReprocessingSelection.DOCUMENT_IDS) {
            if (request.documentIds() == null || request.documentIds().isEmpty()) {
                throw new IllegalArgumentException("DOCUMENT_IDS selection requires a non-empty documentIds list");
            }
            return preparation.ownedIds(knowledgeBaseId, request.documentIds());
        }
        if (request.documentIds() != null && !request.documentIds().isEmpty()) {
            throw new IllegalArgumentException(selection + " selection does not accept documentIds");
        }
        return preparation.allOwned(knowledgeBaseId);
    }

    private ReprocessingDocumentPreparation.Profile capturedProfile(AiProfileNode profile) {
        return new ReprocessingDocumentPreparation.Profile(profile.getId(), profile.getRevision(),
            profile.getBaseUrl(), profile.getEmbeddingModel(), profile.getEmbeddingDimensions(),
            profile.getTokenizerIdValue());
    }

    private boolean documentTargetStillActive(SchemaReprocessingPlanNode plan, AiProfileNode profile) {
        ReprocessingDocumentPreparation.Identity identity = preparation.identity(capturedProfile(profile));
        return plan.getEmbeddingSpaceId().equals(identity.embeddingSpaceId())
            && plan.getExpectedChunkerRevision().equals(identity.targetRevision());
    }

    private ChunkMigrationSnapshot.ChunkTarget savedChunkTarget(ReprocessingDocumentExecutor.ChunkTarget chunk) {
        if (chunk == null) return null;
        return new ChunkMigrationSnapshot.ChunkTarget(
            chunk.strategyName(), chunk.strategyRevision(), chunk.targetTokens(), chunk.overlapTokens(),
            chunk.hardCharacterLimit(), chunk.parentTargetTokens(), chunk.parentHardCharacterLimit(),
            chunk.parentMaxPages(), chunk.contextHeaderMaxTokens(), chunk.contextHeaderMaxCharacters(),
            chunk.tokenizerId(), chunk.tokenizerRevision(), chunk.tokenCountMode(),
            chunk.representationRevision(), chunk.settingsHash());
    }

    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private SchemaReprocessingItemNode createItem(
        String planId, Summary document, String priorItemId
    ) {
        SchemaReprocessingItemNode item = new SchemaReprocessingItemNode();
        item.setId(UUID.randomUUID().toString());
        item.setPlanId(planId);
        item.setDocumentId(document.id());
        item.setDocumentSha256(document.sha256());
        item.setStatus(SchemaReprocessingItemStatus.QUEUED);
        item.setRetryable(true);
        item.setPriorItemId(priorItemId);
        return item;
    }

    private void scheduleAfterCommit(SchemaReprocessingPlanNode plan) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(plan);
                }
            });
            return;
        }
        schedule(plan);
    }

    private void schedule(SchemaReprocessingPlanNode plan) {
        try {
            executor.execute(() -> execute(plan.getId()));
        } catch (RuntimeException exception) {
            plan.setStatus(SchemaReprocessingPlanStatus.FAILED);
            plan.setCompletedAt(Instant.now());
            planRepository.save(plan);
            throw new ConflictException("Reprocessing queue is full");
        }
    }

    private void completeItem(
        SchemaReprocessingItemNode item, String workerId, SchemaReprocessingItemStatus status,
        String failureCategory, boolean retryable
    ) {
        if (!Long.valueOf(1).equals(itemRepository.complete(
            item.getId(), workerId, status, failureCategory, retryable, Instant.now()))) {
            throw new ConflictException("Reprocessing item claim ownership changed: " + item.getId());
        }
    }
    private int count(List<SchemaReprocessingItemNode> items, SchemaReprocessingItemStatus status) {
        return (int) items.stream().filter(value -> value.getStatus() == status).count();
    }
    private SchemaReprocessingPlanNode requirePlan(String knowledgeBaseId, String planId) {
        return planRepository.findByIdAndKnowledgeBaseId(planId, knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Reprocessing plan not found in knowledge base: " + planId));
    }
    private PlanResponse toResponse(
        SchemaReprocessingPlanNode plan, int page, int size,
        List<SchemaReprocessingItemNode> items, long total
    ) {
        List<PlanItemResponse> responses = items.stream().map(value -> new PlanItemResponse(
            value.getId(), value.getDocumentId(), value.getDocumentSha256(), value.getStatus(), value.getFailureCategory(),
            value.isRetryable(), value.getPriorItemId(), value.getStartedAt(), value.getCompletedAt())).toList();
        return new PlanResponse(
            plan.getId(), plan.getReason(), plan.getSelection(), plan.getExpectedChunkerRevision(),
            plan.getStatus(), plan.getDraftId(), plan.getKnowledgeBaseId(),
            plan.getSchemaId(), plan.getSchemaContentHash(), plan.getAiProfileId(), plan.getAiProfileRevision(),
            plan.getRetryOfPlanId(), plan.getTotalDocuments(), plan.getQueuedDocuments(), plan.getRunningDocuments(),
            plan.getSucceededDocuments(), plan.getFailedDocuments(), plan.getStaleDocuments(), plan.getBlockedDocuments(),
            plan.getCreatedAt(), plan.getStartedAt(), plan.getCompletedAt(),
            new PlanItemPageResponse(page, size, total, responses));
    }
    private String statusLocation(SchemaReprocessingPlanNode plan) {
        return "/api/v1/knowledge-bases/" + plan.getKnowledgeBaseId() + "/reprocessing-plans/" + plan.getId();
    }

    private PlanSummaryResponse toSummary(
        SchemaReprocessingPlanNode plan, boolean latest, boolean targetCurrent
    ) {
        boolean terminal = plan.getStatus() != SchemaReprocessingPlanStatus.QUEUED
            && plan.getStatus() != SchemaReprocessingPlanStatus.RUNNING;
        int unresolved = plan.getTotalDocuments() - plan.getSucceededDocuments();
        return new PlanSummaryResponse(
            plan.getId(), plan.getReason(), plan.getSelection(), plan.getExpectedChunkerRevision(),
            plan.getStatus(), plan.getDraftId(), plan.getSchemaId(), plan.getSchemaContentHash(),
            plan.getRetryOfPlanId(), plan.getTotalDocuments(), plan.getQueuedDocuments(), plan.getRunningDocuments(),
            plan.getSucceededDocuments(), plan.getFailedDocuments(), plan.getStaleDocuments(),
            plan.getBlockedDocuments(), latest, targetCurrent, terminal && targetCurrent && unresolved > 0,
            plan.getCreatedAt(), plan.getStartedAt(), plan.getCompletedAt(), statusLocation(plan));
    }

    private record MigrationTarget(
        String schemaId,
        String schemaContentHash,
        String aiProfileId,
        long aiProfileRevision,
        String embeddingSpaceId,
        String expectedChunkerRevision
    ) { }

    private record ChunkMigrationDocumentClassification(
        String classification,
        ChunkMigrationSnapshot.DocumentTarget target
    ) { }

    private record ChunkMigrationEvaluation(
        KnowledgeBaseNode knowledgeBase,
        SchemaDefinitionNode schema,
        AiProfileNode profile,
        String embeddingSpace,
        ChunkMigrationSnapshot.ChunkTarget chunkTarget,
        MigrationTarget target,
        List<ChunkMigrationBlocker> blockers,
        List<Summary> selectedDocuments,
        Map<String, ChunkMigrationSnapshot.DocumentTarget> documentTargets,
        Map<String, ChunkMigrationDocumentClassification> classifications,
        long noChunks,
        long outdated,
        long current
    ) {
        private boolean ready() {
            return blockers.isEmpty();
        }
    }
}
