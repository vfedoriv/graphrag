package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.ai.contracts.AiProfileAccess;

import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.knowledgebase.api.error.KnowledgeBaseNotEmptyException;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.knowledgebase.ports.OwnedDocumentState;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseArtifactCleanup;
import java.util.Objects;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
@Slf4j
public class KnowledgeBaseService implements io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseProfiles {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AiProfileAccess aiProfileService;
    private final OwnedDocumentState ownedDocuments;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final EmbeddingCompatibility compatibility;
    private final KnowledgeBaseArtifactCleanup artifactCleanup;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository, AiProfileAccess aiProfileService,
        OwnedDocumentState ownedDocuments, KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        EmbeddingCompatibility compatibility, KnowledgeBaseArtifactCleanup artifactCleanup) {
        this.knowledgeBaseRepository = Objects.requireNonNull(knowledgeBaseRepository);
        this.aiProfileService = Objects.requireNonNull(aiProfileService);
        this.ownedDocuments = Objects.requireNonNull(ownedDocuments);
        this.knowledgeBaseLifecycleService = Objects.requireNonNull(knowledgeBaseLifecycleService);
        this.compatibility = Objects.requireNonNull(compatibility);
        this.artifactCleanup = Objects.requireNonNull(artifactCleanup);
    }

    @RelationalTransactional
    public KnowledgeBaseNode create(String id, String name) {
        log.info("Creating knowledge base: knowledgeBaseId={}", id);
        if (knowledgeBaseRepository.existsById(id)) {
            throw new ConflictException("Knowledge base already exists: " + id);
        }
        KnowledgeBaseNode saved = knowledgeBaseLifecycleService.provision(id, name);
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
    public ProfileFacts activeAiProfile(String knowledgeBaseId) {
        KnowledgeBaseNode knowledgeBase = get(knowledgeBaseId);
        String profileId = knowledgeBase.getActiveAiProfileId();
        if (profileId == null || profileId.isBlank()) {
            return aiProfileService.defaultFacts();
        }
        return aiProfileService.require(profileId);
    }

    @RelationalTransactional(readOnly = true)
    public ProfileFacts aiProfile(String profileId) {
        return aiProfileService.require(profileId);
    }

    @RelationalTransactional(readOnly = true)
    public io.github.vfedoriv.graphrag.ai.contracts.ProfileView getActiveAiProfile(String knowledgeBaseId) {
        return aiProfileService.inspect(activeAiProfile(knowledgeBaseId).getId());
    }

    @RelationalTransactional
    public KnowledgeBaseNode updateActiveAiProfile(String knowledgeBaseId, String profileId) {
        KnowledgeBaseNode knowledgeBase = get(knowledgeBaseId);
        ProfileFacts profile = aiProfileService.require(profileId);
        compatibility.requireCompatible(knowledgeBaseId, EmbeddingTarget.derive(
            profile.getBaseUrl(), profile.getEmbeddingModel(), profile.getEmbeddingDimensions(),
            profile.getTokenizerId() == null ? null : profile.getTokenizerId().value()));
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

    @RelationalTransactional
    public void delete(String id) {
        log.info("Deleting knowledge base: knowledgeBaseId={}", id);
        if (!knowledgeBaseRepository.existsById(id)) {
            throw new NotFoundException("Knowledge base not found: " + id);
        }
        long documentCount = ownedDocuments.countByKnowledgeBaseId(id);
        if (documentCount > 0) {
            throw new KnowledgeBaseNotEmptyException(id, documentCount);
        }
        artifactCleanup.cleanupKnowledgeBaseArtifacts(id);
        knowledgeBaseRepository.deleteById(id);
        log.info("Knowledge base deleted: knowledgeBaseId={}", id);
    }
}
