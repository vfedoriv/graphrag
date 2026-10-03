package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.SchemaWorkflowKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationProfiles;
import org.springframework.stereotype.Component;

@Component
public class EvaluationProfilesAdapter implements EvaluationProfiles {
    private final SchemaWorkflowKnowledgeBaseFacts facts;
    public EvaluationProfilesAdapter(SchemaWorkflowKnowledgeBaseFacts facts) { this.facts = facts; }
    @Override public Profile activeProfile(String knowledgeBaseId) {
        SchemaWorkflowKnowledgeBaseFacts.Profile profile = facts.activeProfile(knowledgeBaseId);
        return new Profile(profile.id(), profile.revision());
    }
}
