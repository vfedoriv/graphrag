package io.github.vfedoriv.graphrag.bootstrap.integration.search;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.SearchKnowledgeBaseAccess;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchKnowledgeBases;
import org.springframework.stereotype.Component;

@Component
public class SearchKnowledgeBaseAdapter implements SearchKnowledgeBases {
    private final SearchKnowledgeBaseAccess knowledgeBases;

    public SearchKnowledgeBaseAdapter(SearchKnowledgeBaseAccess knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public Facts require(String knowledgeBaseId) {
        SearchKnowledgeBaseAccess.Facts facts = knowledgeBases.require(knowledgeBaseId);
        return new Facts(facts.knowledgeBaseId(), facts.activeSchemaId(), facts.activeAiProfileId());
    }

    @Override
    public boolean exists(String knowledgeBaseId) {
        return knowledgeBases.exists(knowledgeBaseId);
    }
}
