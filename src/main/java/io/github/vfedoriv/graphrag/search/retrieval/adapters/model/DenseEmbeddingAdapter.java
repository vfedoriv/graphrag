package io.github.vfedoriv.graphrag.search.retrieval.adapters.model;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.ai.models.EmbeddingClient;
import io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles;
import io.github.vfedoriv.graphrag.search.retrieval.ports.SearchEmbeddingModel;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class DenseEmbeddingAdapter implements SearchEmbeddingModel {
    private final SearchProfiles profiles;
    private final EmbeddingCompatibility compatibility;
    private final ProfileScopedAiClientResolver clients;
    public DenseEmbeddingAdapter(SearchProfiles profiles, EmbeddingCompatibility compatibility,
                                 ProfileScopedAiClientResolver clients) {
        this.profiles = profiles; this.compatibility = compatibility; this.clients = clients;
    }
    public Batch embed(String knowledgeBaseId, List<String> texts) {
        SearchProfiles.Profile profile = profiles.forKnowledgeBase(knowledgeBaseId);
        EmbeddingTarget target = profile.target();
        compatibility.requireCompatible(knowledgeBaseId, target);
        EmbeddingClient client = clients.embeddingClient();
        if (client == null) { throw new IllegalStateException("Embedding model is not configured"); }
        return new Batch(target, AiProfileContext.withProfile(profile.id(), () -> client.embed(texts)));
    }
}
