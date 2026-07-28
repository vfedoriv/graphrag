package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.CreateDraftRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DraftResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.UpdateDraftRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.UpdateGuidanceRequest;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;

@Service
@Slf4j
public class SchemaDraftLifecycleService {
    private final SchemaDraftRepository draftRepository;
    private final SchemaDraftSourceRepository sourceRepository;
    private final SchemaDraftAnalysisRunRepository runRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftGuidanceMapper guidanceMapper;
    private final BinaryStorageService storageService;
    private final SchemaDraftWorkflowNavigationService workflowNavigationService;

    public SchemaDraftLifecycleService(
        SchemaDraftRepository draftRepository,
        SchemaDraftSourceRepository sourceRepository,
        SchemaDraftAnalysisRunRepository runRepository,
        SchemaDefinitionRepository schemaRepository,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        KnowledgeBaseService knowledgeBaseService,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftGuidanceMapper guidanceMapper,
        BinaryStorageService storageService,
        SchemaDraftWorkflowNavigationService workflowNavigationService
    ) {
        this.draftRepository = draftRepository;
        this.sourceRepository = sourceRepository;
        this.runRepository = runRepository;
        this.schemaRepository = schemaRepository;
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.jsonSupport = jsonSupport;
        this.guidanceMapper = guidanceMapper;
        this.storageService = storageService;
        this.workflowNavigationService = workflowNavigationService;
    }

    @GraphTransactional
    public DraftResponse create(String knowledgeBaseId, CreateDraftRequest request) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        String targetName = request.targetName().strip();
        SchemaDefinitionNode base = validateBase(knowledgeBaseId, request.baseSchemaId(), targetName, request.targetVersion());
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        String guidanceJson = guidanceMapper.canonical(request.guidance());
        Instant now = Instant.now();
        SchemaDraftNode draft = new SchemaDraftNode();
        draft.setId(UUID.randomUUID().toString());
        draft.setKnowledgeBaseId(knowledgeBaseId);
        draft.setTargetName(targetName);
        draft.setTargetVersion(request.targetVersion());
        draft.setBaseSchemaId(base == null ? null : base.getId());
        draft.setStatus(SchemaDraftStatus.OPEN);
        draft.setRevision(0);
        draft.setGuidanceRevision(0);
        draft.setGuidanceJson(guidanceJson);
        draft.setGuidanceFingerprint(jsonSupport.fingerprint(guidanceJson));
        draft.setActiveAiProfileId(profile.getId());
        draft.setActiveAiProfileRevision(profile.getRevision());
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        SchemaDraftNode saved = draftRepository.save(draft);
        draftRepository.attachToKnowledgeBase(knowledgeBaseId, saved.getId());
        log.info("Schema draft created: knowledgeBaseId={}, draftId={}, revision={}, baseSchemaPresent={}",
            knowledgeBaseId, saved.getId(), saved.getRevision(), saved.getBaseSchemaId() != null);
        return toResponse(saved);
    }

    @GraphTransactional(readOnly = true)
    public List<DraftResponse> list(String knowledgeBaseId) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        List<SchemaDraftNode> drafts = draftRepository.findByKnowledgeBaseIdOrderByUpdatedAtDesc(knowledgeBaseId);
        Map<String, SchemaDraftWorkflowNavigationService.WorkflowReferences> references =
            workflowNavigationService.references(drafts);
        return drafts.stream().map(draft -> toResponse(draft, references.get(draft.getId()))).toList();
    }

    @GraphTransactional(readOnly = true)
    public DraftResponse get(String knowledgeBaseId, String draftId) {
        return toResponse(requireOwned(knowledgeBaseId, draftId));
    }

    @GraphTransactional
    public DraftResponse update(String knowledgeBaseId, String draftId, UpdateDraftRequest request) {
        SchemaDraftNode draft = requireMutable(knowledgeBaseId, draftId, request.revision());
        validateBase(knowledgeBaseId, draft.getBaseSchemaId(), request.targetName().strip(), request.targetVersion());
        draft.setTargetName(request.targetName().strip());
        draft.setTargetVersion(request.targetVersion());
        advance(draft);
        return toResponse(draftRepository.save(draft));
    }

    @GraphTransactional
    public DraftResponse updateGuidance(String knowledgeBaseId, String draftId, UpdateGuidanceRequest request) {
        SchemaDraftNode draft = requireMutable(knowledgeBaseId, draftId, request.revision());
        String canonical = guidanceMapper.canonical(request.guidance());
        if (!canonical.equals(draft.getGuidanceJson())) {
            draft.setGuidanceJson(canonical);
            draft.setGuidanceFingerprint(jsonSupport.fingerprint(canonical));
            draft.setGuidanceRevision(draft.getGuidanceRevision() + 1);
            draft.setCurrentAggregateId(null);
            advance(draft);
        }
        return toResponse(draftRepository.save(draft));
    }

    @GraphTransactional
    public void delete(String knowledgeBaseId, String draftId, long revision) {
        SchemaDraftNode draft = requireMutable(knowledgeBaseId, draftId, revision);
        if (runRepository.findFirstByDraftIdAndStatusOrderByCreatedAtDesc(draftId, SchemaDraftAnalysisStatus.RUNNING).isPresent()) {
            throw new ConflictException("Schema draft has a running analysis: " + draftId);
        }
        List<SchemaDraftSourceNode> sources = sourceRepository.findByDraftIdOrderByCreatedAtAsc(draftId);
        for (SchemaDraftSourceNode source : sources) {
            deleteContent(source);
        }
        draftRepository.deleteOwnedGraph(knowledgeBaseId, draftId);
        log.info("Schema draft deleted: knowledgeBaseId={}, draftId={}, sourceCount={}", knowledgeBaseId, draftId, sources.size());
    }

    @GraphTransactional(readOnly = true)
    public SchemaDraftNode requireOwned(String knowledgeBaseId, String draftId) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        return draftRepository.findByIdAndKnowledgeBaseId(draftId, knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Schema draft not found in knowledge base: " + draftId));
    }

    @GraphTransactional(readOnly = true)
    public SchemaDraftNode requireMutable(String knowledgeBaseId, String draftId, long revision) {
        SchemaDraftNode draft = requireOwned(knowledgeBaseId, draftId);
        if (draft.getStatus() != SchemaDraftStatus.OPEN) {
            throw new ConflictException("Published schema draft is read-only: " + draftId);
        }
        if (draft.getRevision() != revision) {
            throw new ConflictException("Stale schema draft revision; current revision is " + draft.getRevision());
        }
        return draft;
    }

    public void advance(SchemaDraftNode draft) {
        draft.setRevision(draft.getRevision() + 1);
        draft.setUpdatedAt(Instant.now());
    }

    private SchemaDefinitionNode validateBase(
        String knowledgeBaseId, String baseSchemaId, String targetName, int targetVersion
    ) {
        if (targetName == null || targetName.isBlank() || targetVersion < 1) {
            throw new IllegalArgumentException("Target schema name and positive version are required");
        }
        if (baseSchemaId == null || baseSchemaId.isBlank()) {
            return null;
        }
        SchemaDefinitionNode base = schemaRepository.findAllByKnowledgeBaseId(knowledgeBaseId).stream()
            .filter(schema -> baseSchemaId.equals(schema.getId()))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Base schema not found in knowledge base: " + baseSchemaId));
        if (!targetName.equals(base.getName())) {
            throw new IllegalArgumentException("Target schema name must match the base schema name");
        }
        if (targetVersion <= base.getVersion()) {
            throw new IllegalArgumentException("Target schema version must be greater than the base schema version");
        }
        return base;
    }

    private void deleteContent(SchemaDraftSourceNode source) {
        if (source.getContentUri() == null || source.getContentUri().isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(source.getContentUri());
            if (Files.exists(storageService.resolvePath(uri))) {
                storageService.delete(uri);
            }
        } catch (Exception exception) {
            throw new ConflictException("Draft source storage cleanup failed for source " + source.getId());
        }
    }

    public DraftResponse toResponse(SchemaDraftNode draft) {
        SchemaDraftWorkflowNavigationService.WorkflowReferences references = workflowNavigationService
            .references(List.of(draft)).get(draft.getId());
        return toResponse(draft, references);
    }

    private DraftResponse toResponse(
        SchemaDraftNode draft, SchemaDraftWorkflowNavigationService.WorkflowReferences references
    ) {
        String currentPublishedHash = draft.getPublicationSchemaId() == null ? null
            : schemaRepository.findById(draft.getPublicationSchemaId()).map(SchemaDefinitionNode::getContentHash).orElse(null);
        return new DraftResponse(draft.getId(), draft.getKnowledgeBaseId(), draft.getTargetName(), draft.getTargetVersion(),
            draft.getBaseSchemaId(), draft.getStatus(), draft.getRevision(), guidanceMapper.read(draft.getGuidanceJson()),
            draft.getGuidanceRevision(),
            draft.getGuidanceFingerprint(), draft.getCurrentAggregateId(), draft.getPublicationSchemaId(),
            draft.getPublicationContentHash(), currentPublishedHash,
            draft.getPublicationContentHash() != null && !draft.getPublicationContentHash().equals(currentPublishedHash),
            draft.getActiveAiProfileId(), draft.getActiveAiProfileRevision(),
            references == null ? null : references.currentAnalysis(),
            references == null ? null : references.latestEvaluation(),
            references == null ? null : references.latestReprocessing(),
            draft.getCreatedAt(), draft.getUpdatedAt());
    }
}
