package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.CreatePlanRequest;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanItemResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.StartPlanResponse;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SchemaReprocessingPlanService {
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentUploadRepository documentRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final SchemaDraftPublicationRepository publicationRepository;
    private final SchemaReprocessingPlanRepository planRepository;
    private final SchemaReprocessingItemRepository itemRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentProcessingService processingService;
    private final SchemaDraftJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;
    private final AiObservationService observationService;

    public SchemaReprocessingPlanService(
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        KnowledgeBaseRepository knowledgeBaseRepository,
        DocumentUploadRepository documentRepository,
        SchemaDefinitionRepository schemaRepository,
        SchemaDraftPublicationRepository publicationRepository,
        SchemaReprocessingPlanRepository planRepository,
        SchemaReprocessingItemRepository itemRepository,
        KnowledgeBaseService knowledgeBaseService,
        DocumentProcessingService processingService,
        SchemaDraftJsonSupport jsonSupport,
        ObjectMapper objectMapper,
        AiObservationService observationService,
        @Qualifier("schemaReprocessingExecutor") TaskExecutor executor
    ) {
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.schemaRepository = schemaRepository;
        this.publicationRepository = publicationRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.processingService = processingService;
        this.jsonSupport = jsonSupport;
        this.objectMapper = objectMapper;
        this.observationService = observationService;
        this.executor = executor;
    }

    @Transactional
    public StartPlanResponse create(String knowledgeBaseId, CreatePlanRequest request) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        SchemaDraftPublicationNode publication = publicationRepository.findByDraftId(request.draftId())
            .filter(value -> knowledgeBaseId.equals(value.getKnowledgeBaseId()) && request.schemaId().equals(value.getSchemaId()))
            .orElseThrow(() -> new NotFoundException("Published draft schema not found in knowledge base"));
        SchemaDefinitionNode schema = requireActiveTarget(knowledgeBaseId, publication.getSchemaId());
        List<DocumentUploadNode> documents = selectedDocuments(knowledgeBaseId, request);
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setId(UUID.randomUUID().toString());
        plan.setDraftId(request.draftId());
        plan.setKnowledgeBaseId(knowledgeBaseId);
        plan.setSchemaId(schema.getId());
        plan.setSchemaContentHash(schema.getContentHash());
        plan.setAiProfileId(profile.getId());
        plan.setAiProfileRevision(profile.getRevision());
        plan.setProcessingOptionsJson(jsonSupport.canonical(request.processingOptions() == null ? Map.of() : request.processingOptions()));
        plan.setStatus(SchemaReprocessingPlanStatus.QUEUED);
        plan.setTotalDocuments(documents.size());
        plan.setQueuedDocuments(documents.size());
        plan.setCreatedAt(Instant.now());
        SchemaReprocessingPlanNode saved = planRepository.save(plan);
        for (DocumentUploadNode document : documents) {
            createItem(saved.getId(), document, null);
        }
        schedule(saved);
        log.info("Schema reprocessing plan queued: planId={}, knowledgeBaseId={}, schemaId={}, documentCount={}, schemaHash={}",
            saved.getId(), knowledgeBaseId, schema.getId(), documents.size(), schema.getContentHash());
        return new StartPlanResponse(saved.getId(), saved.getStatus(), statusLocation(saved));
    }

    @Transactional
    public StartPlanResponse retry(String knowledgeBaseId, String planId, boolean resnapshot) {
        if (!resnapshot) throw new IllegalArgumentException("Retry requires explicit unresolved-document resnapshot");
        SchemaReprocessingPlanNode prior = requirePlan(knowledgeBaseId, planId);
        if (prior.getStatus() == SchemaReprocessingPlanStatus.QUEUED || prior.getStatus() == SchemaReprocessingPlanStatus.RUNNING) {
            throw new ConflictException("Reprocessing plan is still running: " + planId);
        }
        SchemaDefinitionNode schema = requireActiveTarget(knowledgeBaseId, prior.getSchemaId());
        List<SchemaReprocessingItemNode> unresolved = itemRepository.findByPlanIdOrderByDocumentIdAsc(planId).stream()
            .filter(value -> value.getStatus() != SchemaReprocessingItemStatus.SUCCEEDED).toList();
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        SchemaReprocessingPlanNode retry = new SchemaReprocessingPlanNode();
        retry.setId(UUID.randomUUID().toString());
        retry.setDraftId(prior.getDraftId());
        retry.setKnowledgeBaseId(knowledgeBaseId);
        retry.setSchemaId(schema.getId());
        retry.setSchemaContentHash(schema.getContentHash());
        retry.setAiProfileId(profile.getId());
        retry.setAiProfileRevision(profile.getRevision());
        retry.setProcessingOptionsJson(prior.getProcessingOptionsJson());
        retry.setRetryOfPlanId(prior.getId());
        retry.setStatus(SchemaReprocessingPlanStatus.QUEUED);
        retry.setTotalDocuments(unresolved.size());
        retry.setQueuedDocuments(unresolved.size());
        retry.setCreatedAt(Instant.now());
        SchemaReprocessingPlanNode saved = planRepository.save(retry);
        for (SchemaReprocessingItemNode priorItem : unresolved) {
            DocumentUploadNode document = documentRepository.findByIdAndKnowledgeBaseId(
                priorItem.getDocumentId(), knowledgeBaseId)
                .orElseThrow(() -> new NotFoundException("Retry document not found in knowledge base: " + priorItem.getDocumentId()));
            createItem(saved.getId(), document, priorItem.getId());
        }
        schedule(saved);
        return new StartPlanResponse(saved.getId(), saved.getStatus(), statusLocation(saved));
    }

    @Transactional(readOnly = true)
    public PlanResponse get(String knowledgeBaseId, String planId, int page, int size) {
        SchemaReprocessingPlanNode plan = requirePlan(knowledgeBaseId, planId);
        Page<SchemaReprocessingItemNode> items = itemRepository.findByPlanIdOrderByDocumentIdAsc(
            planId, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size))));
        return toResponse(plan, items.getContent(), items.getTotalElements());
    }

    void execute(String planId) {
        String workerId = UUID.randomUUID().toString();
        if (!Long.valueOf(1).equals(planRepository.claim(planId, workerId, Instant.now()))) return;
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
                        blockRemaining(planId);
                        break;
                    }
                    processItem(plan, item);
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

    private void processItem(SchemaReprocessingPlanNode plan, SchemaReprocessingItemNode item) {
        item.setStatus(SchemaReprocessingItemStatus.RUNNING);
        item.setStartedAt(Instant.now());
        itemRepository.save(item);
        DocumentUploadNode current = documentRepository.findByIdAndKnowledgeBaseId(
            item.getDocumentId(), plan.getKnowledgeBaseId()).orElse(null);
        if (current == null || !item.getDocumentSha256().equals(current.getSha256())) {
            completeItem(item, SchemaReprocessingItemStatus.STALE_SOURCE, "SOURCE_CHANGED", false);
            return;
        }
        try {
            Map<String, Object> options = objectMapper.readValue(
                plan.getProcessingOptionsJson(), new TypeReference<Map<String, Object>>() { });
            DocumentUploadNode processed = AiProfileContext.withProfile(plan.getAiProfileId(),
                () -> processingService.process(item.getDocumentId(), true, options));
            if (processed.getStatus() == DocumentStatus.COMPLETED) {
                completeItem(item, SchemaReprocessingItemStatus.SUCCEEDED, null, false);
            } else {
                completeItem(item, SchemaReprocessingItemStatus.FAILED, "DOCUMENT_PROCESSING_FAILED", true);
            }
        } catch (Exception exception) {
            completeItem(item, SchemaReprocessingItemStatus.FAILED, exception.getClass().getSimpleName(), true);
            log.warn("Schema reprocessing item failed: planId={}, documentId={}, exceptionType={}",
                plan.getId(), item.getDocumentId(), LogMetadata.exceptionType(exception));
        }
    }

    private void aggregate(SchemaReprocessingPlanNode plan) {
        List<SchemaReprocessingItemNode> items = itemRepository.findByPlanIdOrderByDocumentIdAsc(plan.getId());
        int queued = count(items, SchemaReprocessingItemStatus.QUEUED);
        int running = count(items, SchemaReprocessingItemStatus.RUNNING);
        int succeeded = count(items, SchemaReprocessingItemStatus.SUCCEEDED);
        int failed = count(items, SchemaReprocessingItemStatus.FAILED) + count(items, SchemaReprocessingItemStatus.INTERRUPTED);
        int stale = count(items, SchemaReprocessingItemStatus.STALE_SOURCE);
        int blocked = count(items, SchemaReprocessingItemStatus.BLOCKED);
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
        planRepository.save(plan);
    }

    private void blockRemaining(String planId) {
        for (SchemaReprocessingItemNode item : itemRepository.findByPlanIdOrderByDocumentIdAsc(planId)) {
            if (item.getStatus() == SchemaReprocessingItemStatus.QUEUED) {
                completeItem(item, SchemaReprocessingItemStatus.BLOCKED, "ACTIVE_SCHEMA_CHANGED", true);
            }
        }
    }

    private boolean targetStillActive(SchemaReprocessingPlanNode plan) {
        KnowledgeBaseNode knowledgeBase = knowledgeBaseRepository.findById(plan.getKnowledgeBaseId()).orElse(null);
        SchemaDefinitionNode schema = schemaRepository.findById(plan.getSchemaId()).orElse(null);
        return knowledgeBase != null && schema != null && plan.getSchemaId().equals(knowledgeBase.getActiveSchemaId())
            && plan.getSchemaContentHash().equals(schema.getContentHash());
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

    private List<DocumentUploadNode> selectedDocuments(String knowledgeBaseId, CreatePlanRequest request) {
        if (request.allDocuments() == (request.documentIds() != null && !request.documentIds().isEmpty())) {
            throw new IllegalArgumentException("Select either allDocuments or an explicit non-empty documentIds list");
        }
        if (request.allDocuments()) return documentRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(knowledgeBaseId);
        List<DocumentUploadNode> documents = new ArrayList<>();
        for (String id : request.documentIds().stream().distinct().toList()) {
            documents.add(documentRepository.findByIdAndKnowledgeBaseId(id, knowledgeBaseId)
                .orElseThrow(() -> new NotFoundException("Document not found in knowledge base: " + id)));
        }
        return List.copyOf(documents);
    }

    private void createItem(String planId, DocumentUploadNode document, String priorItemId) {
        SchemaReprocessingItemNode item = new SchemaReprocessingItemNode();
        item.setId(UUID.randomUUID().toString());
        item.setPlanId(planId);
        item.setDocumentId(document.getId());
        item.setDocumentSha256(document.getSha256());
        item.setStatus(SchemaReprocessingItemStatus.QUEUED);
        item.setRetryable(true);
        item.setPriorItemId(priorItemId);
        itemRepository.save(item);
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
        SchemaReprocessingItemNode item, SchemaReprocessingItemStatus status, String failureCategory, boolean retryable
    ) {
        item.setStatus(status);
        item.setFailureCategory(failureCategory);
        item.setRetryable(retryable);
        item.setCompletedAt(Instant.now());
        itemRepository.save(item);
    }
    private int count(List<SchemaReprocessingItemNode> items, SchemaReprocessingItemStatus status) {
        return (int) items.stream().filter(value -> value.getStatus() == status).count();
    }
    private SchemaReprocessingPlanNode requirePlan(String knowledgeBaseId, String planId) {
        return planRepository.findByIdAndKnowledgeBaseId(planId, knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Reprocessing plan not found in knowledge base: " + planId));
    }
    private PlanResponse toResponse(SchemaReprocessingPlanNode plan, List<SchemaReprocessingItemNode> items, long total) {
        List<PlanItemResponse> responses = items.stream().map(value -> new PlanItemResponse(
            value.getId(), value.getDocumentId(), value.getDocumentSha256(), value.getStatus(), value.getFailureCategory(),
            value.isRetryable(), value.getPriorItemId(), value.getStartedAt(), value.getCompletedAt())).toList();
        return new PlanResponse(plan.getId(), plan.getStatus(), plan.getDraftId(), plan.getKnowledgeBaseId(),
            plan.getSchemaId(), plan.getSchemaContentHash(), plan.getAiProfileId(), plan.getAiProfileRevision(),
            plan.getRetryOfPlanId(), plan.getTotalDocuments(), plan.getQueuedDocuments(), plan.getRunningDocuments(),
            plan.getSucceededDocuments(), plan.getFailedDocuments(), plan.getStaleDocuments(), plan.getBlockedDocuments(),
            plan.getCreatedAt(), plan.getStartedAt(), plan.getCompletedAt(), responses, total);
    }
    private String statusLocation(SchemaReprocessingPlanNode plan) {
        return "/api/v1/knowledge-bases/" + plan.getKnowledgeBaseId() + "/reprocessing-plans/" + plan.getId();
    }
}
