package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.DraftKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;
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
        ProfileFacts profile = knowledgeBases.activeAiProfile(knowledgeBaseId);
        return new Profile(profile.getId(), profile.getRevision(), profile.getTimeoutSeconds(), profile.getMaxRetries());
    }
}
