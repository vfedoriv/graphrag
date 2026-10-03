package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.DraftKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftKnowledgeBases;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DraftKnowledgeBasesAdapter implements DraftKnowledgeBases {
    private final DraftKnowledgeBaseFacts facts;

    public DraftKnowledgeBasesAdapter(DraftKnowledgeBaseFacts facts) {
        this.facts = facts;
    }

    @Override
    public void requireManaged(String knowledgeBaseId) {
        facts.requireManaged(knowledgeBaseId);
    }

    @Override
    public Optional<String> activeSchemaId(String knowledgeBaseId) {
        return facts.activeSchemaId(knowledgeBaseId);
    }

    @Override
    public Profile activeProfile(String knowledgeBaseId) {
        DraftKnowledgeBaseFacts.Profile profile = facts.activeProfile(knowledgeBaseId);
        return new Profile(profile.id(), profile.revision(), profile.timeoutSeconds(), profile.maxRetries());
    }
}
