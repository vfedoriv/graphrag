package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.ai.contracts.AiProfileAccess;

import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
public class KnowledgeBaseLifecycleService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AiProfileAccess aiProfileService;

    public KnowledgeBaseLifecycleService(KnowledgeBaseRepository knowledgeBaseRepository, AiProfileAccess aiProfileService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.aiProfileService = aiProfileService;
    }

    @RelationalTransactional
    public KnowledgeBaseNode provision(String id, String name) {
        return knowledgeBaseRepository.findById(id).orElseGet(() -> create(id, name));
    }

    @RelationalTransactional(readOnly = true)
    public KnowledgeBaseNode requireManaged(String id) {
        return knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + id));
    }

    private KnowledgeBaseNode create(String id, String name) {
        KnowledgeBaseNode node = new KnowledgeBaseNode();
        node.setId(id);
        node.setName(name);
        node.setActiveAiProfileId(aiProfileService.defaultFacts().getId());
        node.setCreatedAt(Instant.now());
        return knowledgeBaseRepository.save(node);
    }
}
