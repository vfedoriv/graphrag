package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final Neo4jClient neo4jClient;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository, Neo4jClient neo4jClient) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.neo4jClient = neo4jClient;
    }

    @Transactional
    public KnowledgeBaseNode create(String id, String name) {
        if (knowledgeBaseRepository.existsById(id)) {
            throw new ConflictException("Knowledge base already exists: " + id);
        }
        KnowledgeBaseNode node = new KnowledgeBaseNode();
        node.setId(id);
        node.setName(name);
        node.setCreatedAt(Instant.now());
        return knowledgeBaseRepository.save(node);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseNode> list() {
        return knowledgeBaseRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseNode get(String id) {
        return knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + id));
    }

    @Transactional
    public KnowledgeBaseNode update(String id, String name) {
        KnowledgeBaseNode kb = get(id);
        kb.setName(name);
        return knowledgeBaseRepository.save(kb);
    }

    @Transactional
    public void delete(String id) {
        if (!knowledgeBaseRepository.existsById(id)) {
            throw new NotFoundException("Knowledge base not found: " + id);
        }
        neo4jClient.query("""
            MATCH (kb:KnowledgeBase {id: $id})
            DETACH DELETE kb
            """)
            .bind(id).to("id")
            .run();
    }
}
