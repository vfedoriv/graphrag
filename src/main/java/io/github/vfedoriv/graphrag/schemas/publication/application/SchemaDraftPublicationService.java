package io.github.vfedoriv.graphrag.schemas.publication.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaStatus;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaRegistryCapabilities;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftAdmissions;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftReviewInputs;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftPublicationLink;
import io.github.vfedoriv.graphrag.schemas.evaluation.contracts.PublicationEvaluationQualifications;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationStatus;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.PublicationReadinessResponse;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.PublicationResponse;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.PublishDraftRequest;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.ReadinessBlockingReason;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SchemaDraftPublicationService {
    private static final Set<String> BLOCKING_CONFLICTS = Set.of(
        "TYPE", "KEY", "RELATIONSHIP_DIRECTION", "PINNED_DEFINITION", "COORDINATE");

    private final DraftAdmissions admissions;
    private final DraftReviewInputs reviewInputs;
    private final SchemaDraftPublicationRepository publicationRepository;
    private final SchemaRegistryCapabilities registry;
    private final PublicationJsonSupport jsonSupport;
    private final PublicationEvaluationQualifications evaluations;
    private final AiObservationService observationService;
    private final PublicationCheckpointService checkpointService;

    public SchemaDraftPublicationService(
        DraftAdmissions admissions,
        DraftReviewInputs reviewInputs,
        SchemaDraftPublicationRepository publicationRepository,
        SchemaRegistryCapabilities registry,
        PublicationJsonSupport jsonSupport,
        PublicationEvaluationQualifications evaluations,
        AiObservationService observationService,
        PublicationCheckpointService checkpointService
    ) {
        this.admissions = admissions;
        this.reviewInputs = reviewInputs;
        this.publicationRepository = publicationRepository;
        this.registry = registry;
        this.jsonSupport = jsonSupport;
        this.evaluations = evaluations;
        this.observationService = observationService;
        this.checkpointService = checkpointService;
    }

    @RelationalTransactional(readOnly = true)
    public PublicationReadinessResponse readiness(String knowledgeBaseId, String draftId) {
        DraftAdmissions.Draft draft = admissions.requireOwned(knowledgeBaseId, draftId);
        DraftReviewInputs.Projection projection = reviewInputs.projection(knowledgeBaseId, draftId);
        String projectionJson = jsonSupport.canonical(jsonSupport.parse(projection.schemaJson()));
        String contentHash = jsonSupport.fingerprint(projectionJson);
        List<ReadinessBlockingReason> reasons = new ArrayList<>();
        List<String> validationErrors;
        try {
            validationErrors = registry.parseAndValidate(projectionJson).errors();
        } catch (RuntimeException exception) {
            validationErrors = List.of(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
        for (int index = 0; index < validationErrors.size(); index++) {
            reasons.add(new ReadinessBlockingReason("registry-validation:" + index, "REGISTRY_VALIDATION", validationErrors.get(index)));
        }
        DraftReviewInputs.Aggregate aggregate = reviewInputs.aggregate(
            knowledgeBaseId, draftId, projection.aggregateRevisionId());
        for (DraftReviewInputs.Conflict conflict : aggregate.conflicts()) {
            if (!conflict.resolved() && BLOCKING_CONFLICTS.contains(conflict.type())) {
                reasons.add(new ReadinessBlockingReason(conflict.id(), "UNRESOLVED_" + conflict.type(), conflict.coordinate()));
            }
        }
        Set<String> decisions = aggregate.decidedIdentities();
        JsonNode candidates = jsonSupport.parse(aggregate.candidatesJson());
        for (JsonNode candidate : candidates) {
            if (contains(candidate.path("origins"), "GUIDED") && !contains(candidate.path("origins"), "OBSERVED")
                && !decisions.contains(candidate.path("identity").asText())) {
                String identity = candidate.path("identity").asText();
                reasons.add(new ReadinessBlockingReason(identity, "PENDING_REQUIRED_GUIDANCE", identity));
            }
        }
        if (registry.identityExistsGlobally(draft.targetName(), draft.targetVersion())
            && draft.publicationSchemaId() == null) {
            reasons.add(new ReadinessBlockingReason("target-identity", "TARGET_IDENTITY_OCCUPIED",
                draft.targetName() + ":" + draft.targetVersion()));
        }
        PublicationEvaluationQualifications.Requirement requirement = evaluations.requirement();
        if (requirement.requiredForPublication()) {
            PublicationEvaluationQualifications.Run evaluation = evaluations.qualifying(
                draftId, draft.revision(), contentHash).orElse(null);
            if (evaluation == null) {
                reasons.add(new ReadinessBlockingReason("evaluation-current", "CURRENT_EVALUATION_REQUIRED",
                    "No completed or partial evaluation matches this draft revision and projection hash"));
            } else if (evaluation.succeededDocuments() < requirement.minimumSuccessfulDocuments()) {
                reasons.add(new ReadinessBlockingReason("evaluation-threshold", "EVALUATION_THRESHOLD_NOT_MET",
                    "Successful held-out documents: " + evaluation.succeededDocuments()));
            }
        }
        return new PublicationReadinessResponse(reasons.isEmpty(), draft.revision(), projection.aggregateRevisionId(),
            contentHash, draft.targetName(), draft.targetVersion(), List.copyOf(reasons));
    }

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
        Optional<SchemaDraftPublicationNode> existing = publicationRepository.findByDraftId(draftId);
        if (existing.isPresent()) {
            SchemaDraftPublicationNode publication = existing.get();
            if (publication.getDraftRevision() != request.revision()
                || !publication.getProjectionContentHash().equals(request.projectionContentHash())) {
                throw new ConflictException("Published draft preconditions do not match the existing publication");
            }
            return publication.getStatus() == SchemaDraftPublicationStatus.COMPLETED
                ? toResponse(publication)
                : resumePublication(publication, admissions.requireOwned(knowledgeBaseId, draftId));
        }
        DraftAdmissions.Draft draft = admissions.requireMutable(knowledgeBaseId, draftId, request.revision());
        PublicationReadinessResponse readiness = readiness(knowledgeBaseId, draftId);
        if (!readiness.ready()) {
            throw new ConflictException("Schema draft is not ready for publication: "
                + readiness.blockingReasons().stream().map(ReadinessBlockingReason::id).toList());
        }
        if (!readiness.projectionContentHash().equals(request.projectionContentHash())) {
            throw new ConflictException("Stale publication projection content hash");
        }
        String targetIdentity = draft.targetName() + ":" + draft.targetVersion();
        if (publicationRepository.findByTargetIdentity(targetIdentity).isPresent()) {
            throw new ConflictException("Schema publication target identity is already claimed: " + targetIdentity);
        }
        SchemaDraftPublicationNode publication = new SchemaDraftPublicationNode();
        publication.setId(UUID.randomUUID().toString());
        publication.setDraftId(draftId);
        publication.setKnowledgeBaseId(knowledgeBaseId);
        publication.setTargetIdentity(targetIdentity);
        publication.setDraftRevision(draft.revision());
        publication.setAggregateRevisionId(readiness.aggregateRevisionId());
        publication.setProjectionContentHash(readiness.projectionContentHash());
        publication.setStatus(SchemaDraftPublicationStatus.PENDING);
        publication.setRetryable(true);
        publication.setCreatedAt(Instant.now());
        SchemaDraftPublicationNode saved = checkpointService.savePublicationIntent(publication);
        return resumePublication(saved, draft);
    }

    private PublicationResponse resumePublication(
        SchemaDraftPublicationNode publication, DraftAdmissions.Draft draft
    ) {
        DraftReviewInputs.Projection projection = reviewInputs.projection(
            publication.getKnowledgeBaseId(), publication.getDraftId());
        String projectionJson = jsonSupport.canonical(jsonSupport.parse(projection.schemaJson()));
        if (!publication.getAggregateRevisionId().equals(projection.aggregateRevisionId())
            || !publication.getProjectionContentHash().equals(jsonSupport.fingerprint(projectionJson))) {
            throw new ConflictException("Publication intent no longer matches the exact aggregate revision");
        }
        SchemaSnapshot schema = resolveOrCreateSchema(
            draft, projectionJson, publication.getKnowledgeBaseId());
        publication.setSchemaId(schema.schemaDefinitionId());
        publication.setStatus(SchemaDraftPublicationStatus.COMPLETED);
        publication.setRetryable(false);
        publication.setFailureCategory(null);
        publication.setCompletedAt(Instant.now());
        SchemaDraftPublicationNode saved = checkpointService.completePublication(publication,
            new DraftPublicationLink.Completion(publication.getKnowledgeBaseId(), publication.getDraftId(),
                publication.getDraftRevision(), publication.getAggregateRevisionId(), draft.persistenceVersion(),
                schema.schemaDefinitionId(), publication.getProjectionContentHash(), publication.getCompletedAt()));
        log.info("Schema draft published: knowledgeBaseId={}, draftId={}, schemaId={}, revision={}, contentHash={}",
            publication.getKnowledgeBaseId(), publication.getDraftId(), schema.schemaDefinitionId(),
            draft.revision(), publication.getProjectionContentHash());
        return toResponse(saved);
    }

    private SchemaSnapshot resolveOrCreateSchema(
        DraftAdmissions.Draft draft, String projectionJson, String knowledgeBaseId
    ) {
        Optional<SchemaSnapshot> matching = registry.findAssociatedByIdentity(
            knowledgeBaseId, draft.targetName(), draft.targetVersion());
        if (matching.isPresent()) {
            SchemaSnapshot existing = matching.get();
            if (!existing.contentHash().equals(jsonSupport.fingerprint(projectionJson))) {
                throw new ConflictException("Existing publication schema content does not match the publication intent");
            }
            return existing;
        }
        return registry.registerGeneratedInactive(projectionJson, knowledgeBaseId);
    }

    @RelationalTransactional(readOnly = true)
    public PublicationResponse get(String knowledgeBaseId, String draftId) {
        admissions.requireOwned(knowledgeBaseId, draftId);
        return toResponse(publicationRepository.findByDraftId(draftId)
            .orElseThrow(() -> new NotFoundException("Schema draft publication not found: " + draftId)));
    }

    private boolean contains(JsonNode array, String value) {
        if (!array.isArray()) return false;
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }

    private PublicationResponse toResponse(SchemaDraftPublicationNode publication) {
        SchemaSnapshot schema = registry.findStoredById(publication.getSchemaId())
            .orElseThrow(() -> new NotFoundException("Published schema no longer exists: " + publication.getSchemaId()));
        boolean active = schema.status()
            == SchemaStatus.ACTIVE;
        return new PublicationResponse(publication.getId(), publication.getDraftId(), publication.getSchemaId(),
            publication.getDraftRevision(), publication.getProjectionContentHash(), schema.contentHash(),
            !publication.getProjectionContentHash().equals(schema.contentHash()), active, publication.getCreatedAt());
    }
}
