package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;

@Service
public class KnowledgeBaseLifecycleService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AiProfileService aiProfileService;

    public KnowledgeBaseLifecycleService(KnowledgeBaseRepository knowledgeBaseRepository, AiProfileService aiProfileService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.aiProfileService = aiProfileService;
    }

    @GraphTransactional
    public KnowledgeBaseNode provision(String id, String name) {
        return knowledgeBaseRepository.findById(id).orElseGet(() -> create(id, name));
    }

    @GraphTransactional(readOnly = true)
    public KnowledgeBaseNode requireManaged(String id) {
        return knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + id));
    }

    private KnowledgeBaseNode create(String id, String name) {
        KnowledgeBaseNode node = new KnowledgeBaseNode();
        node.setId(id);
        node.setName(name);
        node.setActiveAiProfileId(aiProfileService.defaultProfile().getId());
        node.setCreatedAt(Instant.now());
        return knowledgeBaseRepository.save(node);
    }
}
