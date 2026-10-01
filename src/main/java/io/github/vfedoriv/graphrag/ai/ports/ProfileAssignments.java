package io.github.vfedoriv.graphrag.ai.ports;

import java.util.List;

public interface ProfileAssignments {
    boolean exists(String profileId);
    List<String> knowledgeBaseIds(String profileId);
}
