package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.SchemaWorkflowKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SchemaWorkflowKnowledgeBaseFactsFacade implements SchemaWorkflowKnowledgeBaseFacts {
    private final KnowledgeBaseLifecycleService lifecycle;
    private final KnowledgeBaseService knowledgeBases;
    private final KnowledgeBaseRepository repository;

    public SchemaWorkflowKnowledgeBaseFactsFacade(KnowledgeBaseLifecycleService lifecycle,
        KnowledgeBaseService knowledgeBases, KnowledgeBaseRepository repository) {
        this.lifecycle = lifecycle;
        this.knowledgeBases = knowledgeBases;
        this.repository = repository;
    }

    @Override public KnowledgeBase requireManaged(String knowledgeBaseId) {
        return map(lifecycle.requireManaged(knowledgeBaseId));
    }

    @Override public Optional<KnowledgeBase> find(String knowledgeBaseId) {
        return repository.findById(knowledgeBaseId).map(this::map);
    }

    @Override public Profile activeProfile(String knowledgeBaseId) {
        ProfileFacts profile = knowledgeBases.activeAiProfile(knowledgeBaseId);
        return new Profile(profile.getId(), profile.getRevision(), profile.getBaseUrl(), profile.getEmbeddingModel(),
            profile.getEmbeddingDimensions(), profile.getTokenizerIdValue());
    }

    private KnowledgeBase map(KnowledgeBaseNode knowledgeBase) {
        return new KnowledgeBase(knowledgeBase.getId(), knowledgeBase.getActiveSchemaId());
    }
}
