package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.dto.AiProfileResponse;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final Neo4jClient neo4jClient;
    private final AiProfileService aiProfileService;
    private final DocumentChunkRepository documentChunkRepository;

    public KnowledgeBaseService(
        KnowledgeBaseRepository knowledgeBaseRepository,
        Neo4jClient neo4jClient,
        AiProfileService aiProfileService,
        DocumentChunkRepository documentChunkRepository
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.neo4jClient = neo4jClient;
        this.aiProfileService = aiProfileService;
        this.documentChunkRepository = documentChunkRepository;
    }

    @Transactional
    public KnowledgeBaseNode create(String id, String name) {
        log.info("Creating knowledge base: knowledgeBaseId={}", id);
        if (knowledgeBaseRepository.existsById(id)) {
            throw new ConflictException("Knowledge base already exists: " + id);
        }
        KnowledgeBaseNode node = new KnowledgeBaseNode();
        node.setId(id);
        node.setName(name);
        node.setActiveAiProfileId(aiProfileService.defaultProfile().getId());
        node.setCreatedAt(Instant.now());
        KnowledgeBaseNode saved = knowledgeBaseRepository.save(node);
        log.info("Knowledge base created: knowledgeBaseId={}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseNode> list() {
        log.info("Listing knowledge bases");
        List<KnowledgeBaseNode> nodes = knowledgeBaseRepository.findAllByOrderByCreatedAtDesc();
        log.info("Knowledge bases listed: count={}", nodes.size());
        return nodes;
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseNode get(String id) {
        log.info("Loading knowledge base: knowledgeBaseId={}", id);
        KnowledgeBaseNode node = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + id));
        log.info("Knowledge base loaded: knowledgeBaseId={}, activeSchemaId={}", node.getId(), node.getActiveSchemaId());
        return node;
    }

    @Transactional
    public AiProfileNode activeAiProfile(String knowledgeBaseId) {
        KnowledgeBaseNode knowledgeBase = get(knowledgeBaseId);
        String profileId = knowledgeBase.getActiveAiProfileId();
        if (profileId == null || profileId.isBlank()) {
            return aiProfileService.defaultProfile();
        }
        return aiProfileService.getNode(profileId);
    }

    @Transactional
    public AiProfileResponse getActiveAiProfile(String knowledgeBaseId) {
        return aiProfileService.toResponse(activeAiProfile(knowledgeBaseId));
    }

    @Transactional
    public KnowledgeBaseNode updateActiveAiProfile(String knowledgeBaseId, String profileId) {
        KnowledgeBaseNode knowledgeBase = get(knowledgeBaseId);
        AiProfileNode profile = aiProfileService.getNode(profileId);
        validateEmbeddingCompatibility(knowledgeBaseId, profile);
        knowledgeBase.setActiveAiProfileId(profile.getId());
        return knowledgeBaseRepository.save(knowledgeBase);
    }

    @Transactional
    public KnowledgeBaseNode update(String id, String name) {
        log.info("Updating knowledge base: knowledgeBaseId={}", id);
        KnowledgeBaseNode kb = get(id);
        kb.setName(name);
        KnowledgeBaseNode saved = knowledgeBaseRepository.save(kb);
        log.info("Knowledge base updated: knowledgeBaseId={}", saved.getId());
        return saved;
    }

    private void validateEmbeddingCompatibility(String knowledgeBaseId, AiProfileNode profile) {
        List<DocumentChunkNode> chunks = documentChunkRepository.findFirstEmbeddedChunkByKnowledgeBaseId(knowledgeBaseId);
        if (chunks.isEmpty()) {
            return;
        }
        DocumentChunkNode chunk = chunks.getFirst();
        int storedDimensions = chunk.getEmbeddingDimensions() > 0
            ? chunk.getEmbeddingDimensions()
            : chunk.getEmbedding() == null ? 0 : chunk.getEmbedding().size();
        String storedModel = chunk.getEmbeddingModel();
        boolean modelCompatible = storedModel == null || storedModel.isBlank() || storedModel.equals(profile.getEmbeddingModel());
        boolean dimensionsCompatible = storedDimensions == 0 || storedDimensions == profile.getEmbeddingDimensions();
        if (!modelCompatible || !dimensionsCompatible) {
            throw new ConflictException(
                "AI profile embedding settings are incompatible with existing knowledge base embeddings: storedModel="
                    + storedModel + ", storedDimensions=" + storedDimensions
                    + ", requestedModel=" + profile.getEmbeddingModel()
                    + ", requestedDimensions=" + profile.getEmbeddingDimensions()
            );
        }
    }

    @Transactional
    public void delete(String id) {
        log.info("Deleting knowledge base: knowledgeBaseId={}", id);
        if (!knowledgeBaseRepository.existsById(id)) {
            throw new NotFoundException("Knowledge base not found: " + id);
        }
        neo4jClient.query("""
            MATCH (kb:KnowledgeBase {id: $id})
            DETACH DELETE kb
            """)
            .bind(id).to("id")
            .run();
        log.info("Knowledge base deleted: knowledgeBaseId={}", id);
    }
}
