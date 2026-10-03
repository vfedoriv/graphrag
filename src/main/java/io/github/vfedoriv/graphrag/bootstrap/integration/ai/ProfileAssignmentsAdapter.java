package io.github.vfedoriv.graphrag.bootstrap.integration.ai;

import io.github.vfedoriv.graphrag.ai.ports.ProfileAssignments;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.AiProfileAssignments;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProfileAssignmentsAdapter implements ProfileAssignments {
    private final AiProfileAssignments knowledgeBases;

    public ProfileAssignmentsAdapter(AiProfileAssignments knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public boolean exists(String profileId) {
        return knowledgeBases.exists(profileId);
    }

    @Override
    public List<String> knowledgeBaseIds(String profileId) {
        return knowledgeBases.knowledgeBaseIds(profileId);
    }
}
