package io.github.vfedoriv.graphrag.search.runs.adapters.model;

import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.ai.contracts.AiProfileAccess;
import io.github.vfedoriv.graphrag.ai.models.AiModelPreparation;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseProfiles;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles;
import org.springframework.stereotype.Component;

/** Maps public AI and knowledge-base facts to search-owned admission values. */
@Component
public class SearchProfileAdapter implements SearchProfiles {
    private final AiProfileAccess profiles;
    private final KnowledgeBaseProfiles knowledgeBases;
    private final AiModelPreparation models;
    public SearchProfileAdapter(AiProfileAccess profiles, KnowledgeBaseProfiles knowledgeBases,
                                AiModelPreparation models) {
        this.profiles = profiles; this.knowledgeBases = knowledgeBases; this.models = models;
    }
    public Profile resolve(String profileId) {
        return facts(profileId == null || profileId.isBlank() ? profiles.defaultFacts() : profiles.require(profileId));
    }
    public Profile forKnowledgeBase(String knowledgeBaseId) {
        return facts(knowledgeBases.activeAiProfile(knowledgeBaseId));
    }
    public void constructChat(String profileId) { models.constructChat(profileId); }
    public void constructEmbedding(String profileId) { models.constructEmbedding(profileId); }
    private Profile facts(ProfileFacts profile) {
        return new Profile(profile.getId(), profile.getRevision(), profile.getBaseUrl(), profile.getChatModel(),
            profile.getEmbeddingModel(), profile.getEmbeddingDimensions(),
            profile.getTokenizerIdValue());
    }
}
