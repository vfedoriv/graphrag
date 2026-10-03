package io.github.vfedoriv.graphrag.knowledgebase.contracts;

import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;

/** Knowledge-base scoped profile resolution with existing missing-resource precedence. */
public interface KnowledgeBaseProfiles {
    ProfileFacts activeAiProfile(String knowledgeBaseId);
    ProfileFacts aiProfile(String profileId);
}
