package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.config.SchemaDraftEvaluationProperties;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ProjectionResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.PublicationReadinessResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.PublicationResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.PublishDraftRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ReadinessBlockingReason;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.infrastructure.persistence.SchemaDraftGraphService;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftConflictRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftDecisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;

@Service
@Slf4j
public class SchemaDraftPublicationService {
    private static final Set<SchemaDraftConflictType> BLOCKING_CONFLICTS = EnumSet.of(
        SchemaDraftConflictType.TYPE, SchemaDraftConflictType.KEY, SchemaDraftConflictType.RELATIONSHIP_DIRECTION,
        SchemaDraftConflictType.PINNED_DEFINITION, SchemaDraftConflictType.COORDINATE);

    private final SchemaDraftLifecycleService lifecycleService;
    private final SchemaDraftReviewService reviewService;
    private final SchemaDraftRepository draftRepository;
    private final SchemaDraftConflictRepository conflictRepository;
    private final SchemaDraftDecisionRepository decisionRepository;
    private final SchemaDraftAggregateRevisionRepository aggregateRepository;
    private final SchemaDraftEvaluationRunRepository evaluationRepository;
    private final SchemaDraftPublicationRepository publicationRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final SchemaRegistryService schemaRegistryService;
    private final SchemaDraftGraphService graphService;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftEvaluationProperties evaluationProperties;
    private final AiObservationService observationService;

    public SchemaDraftPublicationService(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftReviewService reviewService,
        SchemaDraftRepository draftRepository,
        SchemaDraftConflictRepository conflictRepository,
        SchemaDraftDecisionRepository decisionRepository,
        SchemaDraftAggregateRevisionRepository aggregateRepository,
        SchemaDraftEvaluationRunRepository evaluationRepository,
        SchemaDraftPublicationRepository publicationRepository,
        SchemaDefinitionRepository schemaRepository,
        SchemaRegistryService schemaRegistryService,
        SchemaDraftGraphService graphService,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftEvaluationProperties evaluationProperties,
        AiObservationService observationService
    ) {
        this.lifecycleService = lifecycleService;
        this.reviewService = reviewService;
        this.draftRepository = draftRepository;
        this.conflictRepository = conflictRepository;
        this.decisionRepository = decisionRepository;
        this.aggregateRepository = aggregateRepository;
        this.evaluationRepository = evaluationRepository;
        this.publicationRepository = publicationRepository;
        this.schemaRepository = schemaRepository;
        this.schemaRegistryService = schemaRegistryService;
        this.graphService = graphService;
        this.jsonSupport = jsonSupport;
        this.evaluationProperties = evaluationProperties;
        this.observationService = observationService;
    }

    @GraphTransactional(readOnly = true)
    public PublicationReadinessResponse readiness(String knowledgeBaseId, String draftId) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        ProjectionResponse projection = reviewService.projection(knowledgeBaseId, draftId);
        String projectionJson = jsonSupport.canonical(projection.schema());
        String contentHash = jsonSupport.fingerprint(projectionJson);
        List<ReadinessBlockingReason> reasons = new ArrayList<>();
        List<String> validationErrors;
        try {
            validationErrors = schemaRegistryService.validateJson(projectionJson);
        } catch (RuntimeException exception) {
            validationErrors = List.of(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
        for (int index = 0; index < validationErrors.size(); index++) {
            reasons.add(new ReadinessBlockingReason("registry-validation:" + index, "REGISTRY_VALIDATION", validationErrors.get(index)));
        }
        for (SchemaDraftConflictNode conflict : conflictRepository.findByAggregateRevision(
            draftId, projection.aggregateRevisionId())) {
            if (!conflict.isResolved() && BLOCKING_CONFLICTS.contains(conflict.getType())) {
                reasons.add(new ReadinessBlockingReason(conflict.getId(), "UNRESOLVED_" + conflict.getType().name(), conflict.getCoordinate()));
            }
        }
        Set<String> decisions = latestDecisions(draftId).keySet();
        JsonNode candidates = jsonSupport.parse(aggregateRepository.findById(projection.aggregateRevisionId())
            .orElseThrow().getCandidatesJson());
        for (JsonNode candidate : candidates) {
            if (contains(candidate.path("origins"), "GUIDED") && !contains(candidate.path("origins"), "OBSERVED")
                && !decisions.contains(candidate.path("identity").asText())) {
                String identity = candidate.path("identity").asText();
                reasons.add(new ReadinessBlockingReason(identity, "PENDING_REQUIRED_GUIDANCE", identity));
            }
        }
        if (Boolean.TRUE.equals(schemaRepository.existsByNameAndVersion(draft.getTargetName(), draft.getTargetVersion()))
            && draft.getPublicationSchemaId() == null) {
            reasons.add(new ReadinessBlockingReason("target-identity", "TARGET_IDENTITY_OCCUPIED",
                draft.getTargetName() + ":" + draft.getTargetVersion()));
        }
        if (evaluationProperties.requiredForPublication()) {
            SchemaDraftEvaluationRunNode evaluation = qualifyingEvaluation(draftId, draft.getRevision(), contentHash);
            if (evaluation == null) {
                reasons.add(new ReadinessBlockingReason("evaluation-current", "CURRENT_EVALUATION_REQUIRED",
                    "No completed or partial evaluation matches this draft revision and projection hash"));
            } else if (evaluation.getSucceededDocuments() < evaluationProperties.minimumSuccessfulDocuments()) {
                reasons.add(new ReadinessBlockingReason("evaluation-threshold", "EVALUATION_THRESHOLD_NOT_MET",
                    "Successful held-out documents: " + evaluation.getSucceededDocuments()));
            }
        }
        return new PublicationReadinessResponse(reasons.isEmpty(), draft.getRevision(), projection.aggregateRevisionId(),
            contentHash, draft.getTargetName(), draft.getTargetVersion(), List.copyOf(reasons));
    }

    @GraphTransactional
    public PublicationResponse publish(String knowledgeBaseId, String draftId, PublishDraftRequest request) {
        try (AiObservationScope workflow = observationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_SCHEMA_DRAFT_PUBLICATION, null,
            Map.of("ai.draft.id", draftId, "knowledge_base.id", knowledgeBaseId,
                "ai.draft.revision", Long.toString(request.revision()))))) {
            try {
                PublicationResponse response = publishObserved(knowledgeBaseId, draftId, request);
                workflow.success();
                return response;
            } catch (RuntimeException exception) {
                workflow.error(exception);
                throw exception;
            }
        }
    }

    private PublicationResponse publishObserved(String knowledgeBaseId, String draftId, PublishDraftRequest request) {
        java.util.Optional<SchemaDraftPublicationNode> existing = publicationRepository.findByDraftId(draftId);
        if (existing.isPresent()) {
            SchemaDraftPublicationNode publication = existing.get();
            if (publication.getDraftRevision() != request.revision()
                || !publication.getProjectionContentHash().equals(request.projectionContentHash())) {
                throw new ConflictException("Published draft preconditions do not match the existing publication");
            }
            return toResponse(publication);
        }
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, request.revision());
        PublicationReadinessResponse readiness = readiness(knowledgeBaseId, draftId);
        if (!readiness.ready()) {
            throw new ConflictException("Schema draft is not ready for publication: "
                + readiness.blockingReasons().stream().map(ReadinessBlockingReason::id).toList());
        }
        if (!readiness.projectionContentHash().equals(request.projectionContentHash())) {
            throw new ConflictException("Stale publication projection content hash");
        }
        String targetIdentity = draft.getTargetName() + ":" + draft.getTargetVersion();
        if (publicationRepository.findByTargetIdentity(targetIdentity).isPresent()) {
            throw new ConflictException("Schema publication target identity is already claimed: " + targetIdentity);
        }
        ProjectionResponse projection = reviewService.projection(knowledgeBaseId, draftId);
        String projectionJson = jsonSupport.canonical(projection.schema());
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(projectionJson, knowledgeBaseId);
        SchemaDraftPublicationNode publication = new SchemaDraftPublicationNode();
        publication.setId(UUID.randomUUID().toString());
        publication.setDraftId(draftId);
        publication.setKnowledgeBaseId(knowledgeBaseId);
        publication.setTargetIdentity(targetIdentity);
        publication.setDraftRevision(draft.getRevision());
        publication.setProjectionContentHash(readiness.projectionContentHash());
        publication.setSchemaId(schema.getId());
        publication.setCreatedAt(Instant.now());
        SchemaDraftPublicationNode saved = publicationRepository.save(publication);
        graphService.attach(draftId, "SchemaDraftPublication", saved.getId());
        draft.setStatus(SchemaDraftStatus.PUBLISHED);
        draft.setPublicationSchemaId(schema.getId());
        draft.setPublicationContentHash(readiness.projectionContentHash());
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);
        log.info("Schema draft published: knowledgeBaseId={}, draftId={}, schemaId={}, revision={}, contentHash={}",
            knowledgeBaseId, draftId, schema.getId(), draft.getRevision(), readiness.projectionContentHash());
        return toResponse(saved);
    }

    @GraphTransactional(readOnly = true)
    public PublicationResponse get(String knowledgeBaseId, String draftId) {
        lifecycleService.requireOwned(knowledgeBaseId, draftId);
        return toResponse(publicationRepository.findByDraftId(draftId)
            .orElseThrow(() -> new NotFoundException("Schema draft publication not found: " + draftId)));
    }

    private SchemaDraftEvaluationRunNode qualifyingEvaluation(String draftId, long revision, String hash) {
        return evaluationRepository.findByDraftIdOrderByCreatedAtDesc(draftId).stream()
            .filter(value -> value.getDraftRevision() == revision && hash.equals(value.getProjectionContentHash()))
            .filter(value -> value.getStatus() == SchemaDraftEvaluationStatus.COMPLETED
                || value.getStatus() == SchemaDraftEvaluationStatus.PARTIAL)
            .findFirst().orElse(null);
    }

    private Map<String, SchemaDraftDecisionNode> latestDecisions(String draftId) {
        Map<String, SchemaDraftDecisionNode> latest = new LinkedHashMap<>();
        decisionRepository.findByDraftIdOrderBySequenceAsc(draftId)
            .forEach(value -> latest.put(value.getCandidateIdentity(), value));
        return latest;
    }

    private boolean contains(JsonNode array, String value) {
        if (!array.isArray()) return false;
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }

    private PublicationResponse toResponse(SchemaDraftPublicationNode publication) {
        SchemaDefinitionNode schema = schemaRepository.findById(publication.getSchemaId())
            .orElseThrow(() -> new NotFoundException("Published schema no longer exists: " + publication.getSchemaId()));
        boolean active = Boolean.TRUE.equals(schemaRepository.existsActiveKnowledgeBaseReference(schema.getId()));
        return new PublicationResponse(publication.getId(), publication.getDraftId(), publication.getSchemaId(),
            publication.getDraftRevision(), publication.getProjectionContentHash(), schema.getContentHash(),
            !publication.getProjectionContentHash().equals(schema.getContentHash()), active, publication.getCreatedAt());
    }
}
