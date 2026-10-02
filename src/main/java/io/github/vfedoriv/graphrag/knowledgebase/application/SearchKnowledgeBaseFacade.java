package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.SearchKnowledgeBaseAccess;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;

@Service
public class SearchKnowledgeBaseFacade implements SearchKnowledgeBaseAccess {
    private final KnowledgeBaseRepository knowledgeBases;

    public SearchKnowledgeBaseFacade(KnowledgeBaseRepository knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public Facts require(String knowledgeBaseId) {
        KnowledgeBaseNode node = knowledgeBases.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
        return new Facts(node.getId(), node.getActiveSchemaId(), node.getActiveAiProfileId());
    }

    @Override
    public boolean exists(String knowledgeBaseId) {
        return knowledgeBases.existsById(knowledgeBaseId);
    }
}
