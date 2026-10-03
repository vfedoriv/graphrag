package io.github.vfedoriv.graphrag.bootstrap.integration.reprocessing;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.SchemaWorkflowKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingKnowledgeBases;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ReprocessingKnowledgeBasesAdapter implements ReprocessingKnowledgeBases {
    private final SchemaWorkflowKnowledgeBaseFacts facts;
    public ReprocessingKnowledgeBasesAdapter(SchemaWorkflowKnowledgeBaseFacts facts) { this.facts = facts; }
    @Override public KnowledgeBase requireManaged(String knowledgeBaseId) { return map(facts.requireManaged(knowledgeBaseId)); }
    @Override public Optional<KnowledgeBase> find(String knowledgeBaseId) { return facts.find(knowledgeBaseId).map(this::map); }
    @Override public Profile activeProfile(String knowledgeBaseId) {
        SchemaWorkflowKnowledgeBaseFacts.Profile profile = facts.activeProfile(knowledgeBaseId);
        return new Profile(profile.id(), profile.revision(), profile.baseUrl(), profile.embeddingModel(), profile.embeddingDimensions(), profile.tokenizerId());
    }
    private KnowledgeBase map(SchemaWorkflowKnowledgeBaseFacts.KnowledgeBase value) { return new KnowledgeBase(value.id(), value.activeSchemaId()); }
}
