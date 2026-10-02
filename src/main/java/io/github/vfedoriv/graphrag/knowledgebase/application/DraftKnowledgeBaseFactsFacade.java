package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.DraftKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DraftKnowledgeBaseFactsFacade implements DraftKnowledgeBaseFacts {
    private final KnowledgeBaseLifecycleService lifecycle;
    private final KnowledgeBaseService knowledgeBases;
    private final KnowledgeBaseRepository repository;

    public DraftKnowledgeBaseFactsFacade(KnowledgeBaseLifecycleService lifecycle,
        KnowledgeBaseService knowledgeBases, KnowledgeBaseRepository repository) {
        this.lifecycle = lifecycle;
        this.knowledgeBases = knowledgeBases;
        this.repository = repository;
    }

    @Override
    public void requireManaged(String knowledgeBaseId) {
        lifecycle.requireManaged(knowledgeBaseId);
    }

    @Override
    public Optional<String> activeSchemaId(String knowledgeBaseId) {
        return repository.findById(knowledgeBaseId).map(KnowledgeBaseNode::getActiveSchemaId);
    }

    @Override
    public Profile activeProfile(String knowledgeBaseId) {
        AiProfileNode profile = knowledgeBases.activeAiProfile(knowledgeBaseId);
        return new Profile(profile.getId(), profile.getRevision(), profile.getTimeoutSeconds(), profile.getMaxRetries());
    }
}
