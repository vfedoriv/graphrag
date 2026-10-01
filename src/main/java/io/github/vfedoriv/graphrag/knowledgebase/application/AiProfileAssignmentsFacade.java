package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.AiProfileAssignments;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiProfileAssignmentsFacade implements AiProfileAssignments {
    private final KnowledgeBaseRepository knowledgeBases;

    public AiProfileAssignmentsFacade(KnowledgeBaseRepository knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public boolean exists(String profileId) {
        return Boolean.TRUE.equals(knowledgeBases.existsAiProfileAssignment(profileId));
    }

    @Override
    public List<String> knowledgeBaseIds(String profileId) {
        List<String> ids = knowledgeBases.findIdsByActiveAiProfileId(profileId);
        return ids == null ? List.of() : List.copyOf(ids);
    }
}
