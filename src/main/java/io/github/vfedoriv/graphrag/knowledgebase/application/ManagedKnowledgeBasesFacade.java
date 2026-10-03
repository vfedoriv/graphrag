package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.ManagedKnowledgeBases;
import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import org.springframework.stereotype.Service;

@Service
public class ManagedKnowledgeBasesFacade implements ManagedKnowledgeBases {
    private final KnowledgeBaseLifecycleService lifecycle;
    public ManagedKnowledgeBasesFacade(KnowledgeBaseLifecycleService lifecycle) { this.lifecycle = lifecycle; }
    @Override public Facts requireManaged(String knowledgeBaseId) {
        KnowledgeBaseNode node = lifecycle.requireManaged(knowledgeBaseId);
        return new Facts(node.getId(), node.getActiveSchemaId());
    }
}
