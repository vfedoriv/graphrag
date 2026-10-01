package io.github.vfedoriv.graphrag.knowledgebase.contracts;

import java.util.List;

public interface AiProfileAssignments {
    boolean exists(String profileId);
    List<String> knowledgeBaseIds(String profileId);
}
