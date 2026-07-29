package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.dto.AiProfileResponse;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.KnowledgeBaseNotEmptyException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
@Slf4j
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AiProfileService aiProfileService;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentUploadRepository documentUploadRepository;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final EmbeddingSpacePolicy embeddingSpacePolicy;
    private final GraphArtifactCleanupService graphArtifactCleanupService;

    @Autowired
    public KnowledgeBaseService(
        KnowledgeBaseRepository knowledgeBaseRepository,
        AiProfileService aiProfileService,
        DocumentChunkRepository documentChunkRepository,
        DocumentUploadRepository documentUploadRepository,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        EmbeddingSpacePolicy embeddingSpacePolicy,
        GraphArtifactCleanupService graphArtifactCleanupService
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.aiProfileService = aiProfileService;
        this.documentChunkRepository = documentChunkRepository;
        this.documentUploadRepository = documentUploadRepository;
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.embeddingSpacePolicy = embeddingSpacePolicy;
        this.graphArtifactCleanupService = graphArtifactCleanupService;
    }

    public KnowledgeBaseService(
        KnowledgeBaseRepository knowledgeBaseRepository,
        AiProfileService aiProfileService,
        DocumentChunkRepository documentChunkRepository
    ) {
        this(
            knowledgeBaseRepository,
            aiProfileService,
            documentChunkRepository,
            null,
            null,
            new EmbeddingSpacePolicy(documentChunkRepository),
            null
        );
    }

    public KnowledgeBaseService(
        KnowledgeBaseRepository knowledgeBaseRepository,
        AiProfileService aiProfileService,
        DocumentChunkRepository documentChunkRepository,
        DocumentUploadRepository documentUploadRepository,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        EmbeddingSpacePolicy embeddingSpacePolicy
    ) {
        this(
            knowledgeBaseRepository,
            aiProfileService,
            documentChunkRepository,
            documentUploadRepository,
            knowledgeBaseLifecycleService,
            embeddingSpacePolicy,
            null
        );
    }

    @RelationalTransactional
    public KnowledgeBaseNode create(String id, String name) {
        log.info("Creating knowledge base: knowledgeBaseId={}", id);
        if (knowledgeBaseRepository.existsById(id)) {
            throw new ConflictException("Knowledge base already exists: " + id);
        }
        KnowledgeBaseNode saved = knowledgeBaseLifecycleService == null
            ? createLegacy(id, name)
            : knowledgeBaseLifecycleService.provision(id, name);
        log.info("Knowledge base created: knowledgeBaseId={}", saved.getId());
        return saved;
    }

    @RelationalTransactional(readOnly = true)
    public List<KnowledgeBaseNode> list() {
        log.info("Listing knowledge bases");
        List<KnowledgeBaseNode> nodes = knowledgeBaseRepository.findAllByOrderByCreatedAtDesc();
        log.info("Knowledge bases listed: count={}", nodes.size());
        return nodes;
    }

    @RelationalTransactional(readOnly = true)
    public KnowledgeBaseNode get(String id) {
        log.info("Loading knowledge base: knowledgeBaseId={}", id);
        KnowledgeBaseNode node = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + id));
        log.info("Knowledge base loaded: knowledgeBaseId={}, activeSchemaId={}", node.getId(), node.getActiveSchemaId());
        return node;
    }

    @RelationalTransactional(readOnly = true)
    public AiProfileNode activeAiProfile(String knowledgeBaseId) {
        KnowledgeBaseNode knowledgeBase = get(knowledgeBaseId);
        String profileId = knowledgeBase.getActiveAiProfileId();
        if (profileId == null || profileId.isBlank()) {
            return aiProfileService.defaultProfile();
        }
        return aiProfileService.getNode(profileId);
    }

    @RelationalTransactional(readOnly = true)
    public AiProfileNode aiProfile(String profileId) {
        return aiProfileService.getNode(profileId);
    }

    @RelationalTransactional(readOnly = true)
    public AiProfileResponse getActiveAiProfile(String knowledgeBaseId) {
        return aiProfileService.toResponse(activeAiProfile(knowledgeBaseId));
    }

    @RelationalTransactional
    public KnowledgeBaseNode updateActiveAiProfile(String knowledgeBaseId, String profileId) {
        KnowledgeBaseNode knowledgeBase = get(knowledgeBaseId);
        AiProfileNode profile = aiProfileService.getNode(profileId);
        embeddingSpacePolicy.requireCompatible(knowledgeBaseId, profile);
        Long version = knowledgeBase.getVersion();
        if (version == null) {
            knowledgeBase.setActiveAiProfileId(profile.getId());
            return knowledgeBaseRepository.save(knowledgeBase);
        }
        if (!knowledgeBaseRepository.assignAiProfile(knowledgeBaseId, version, profile.getId())) {
            throw new ConflictException("Knowledge base was concurrently modified: " + knowledgeBaseId);
        }
        return get(knowledgeBaseId);
    }

    @RelationalTransactional
    public KnowledgeBaseNode update(String id, String name) {
        log.info("Updating knowledge base: knowledgeBaseId={}", id);
        KnowledgeBaseNode kb = get(id);
        kb.setName(name);
        KnowledgeBaseNode saved = knowledgeBaseRepository.save(kb);
        log.info("Knowledge base updated: knowledgeBaseId={}", saved.getId());
        return saved;
    }

    private KnowledgeBaseNode createLegacy(String id, String name) {
        KnowledgeBaseNode node = new KnowledgeBaseNode();
        node.setId(id);
        node.setName(name);
        node.setActiveAiProfileId(aiProfileService.defaultProfile().getId());
        node.setCreatedAt(Instant.now());
        return knowledgeBaseRepository.save(node);
    }

    @RelationalTransactional
    public void delete(String id) {
        log.info("Deleting knowledge base: knowledgeBaseId={}", id);
        if (!knowledgeBaseRepository.existsById(id)) {
            throw new NotFoundException("Knowledge base not found: " + id);
        }
        long documentCount = documentUploadRepository == null ? 0 : documentUploadRepository.countByKnowledgeBaseId(id);
        if (documentCount > 0) {
            throw new KnowledgeBaseNotEmptyException(id, documentCount);
        }
        if (graphArtifactCleanupService != null) {
            graphArtifactCleanupService.cleanupKnowledgeBaseArtifacts(id);
        }
        knowledgeBaseRepository.deleteById(id);
        log.info("Knowledge base deleted: knowledgeBaseId={}", id);
    }
}
