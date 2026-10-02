package io.github.vfedoriv.graphrag.search.runs.adapters.model;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.service.AiProfileService;
import io.github.vfedoriv.graphrag.service.AiRuntimeModelFactory;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles;
import org.springframework.stereotype.Component;

/** Exact legacy AI construction seams retained for roadmap step nine. */
@Component
public class SearchProfileAdapter implements SearchProfiles {
    private final AiProfileService profiles;
    private final KnowledgeBaseService knowledgeBases;
    private final AiRuntimeModelFactory models;
    public SearchProfileAdapter(AiProfileService profiles, KnowledgeBaseService knowledgeBases,
                                AiRuntimeModelFactory models) {
        this.profiles = profiles; this.knowledgeBases = knowledgeBases; this.models = models;
    }
    public Profile resolve(String profileId) {
        return facts(profileId == null || profileId.isBlank() ? profiles.defaultProfile() : profiles.getNode(profileId));
    }
    public Profile forKnowledgeBase(String knowledgeBaseId) {
        return facts(knowledgeBases.activeAiProfile(knowledgeBaseId));
    }
    public void constructChat(String profileId) { models.chatModel(profileId); }
    public void constructEmbedding(String profileId) { models.embeddingModel(profileId); }
    private Profile facts(AiProfileNode profile) {
        return new Profile(profile.getId(), profile.getRevision(), profile.getBaseUrl(), profile.getChatModel(),
            profile.getEmbeddingModel(), profile.getEmbeddingDimensions(),
            profile.getTokenizerIdValue());
    }
}
