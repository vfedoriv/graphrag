package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
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

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository, Neo4jClient neo4jClient) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.neo4jClient = neo4jClient;
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
    public KnowledgeBaseNode update(String id, String name) {
        log.info("Updating knowledge base: knowledgeBaseId={}", id);
        KnowledgeBaseNode kb = get(id);
        kb.setName(name);
        KnowledgeBaseNode saved = knowledgeBaseRepository.save(kb);
        log.info("Knowledge base updated: knowledgeBaseId={}", saved.getId());
        return saved;
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
