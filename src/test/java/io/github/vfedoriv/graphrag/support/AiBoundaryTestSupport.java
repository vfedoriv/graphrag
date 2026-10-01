package io.github.vfedoriv.graphrag.support;

import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.bootstrap.integration.ai.StoredEmbeddingInformationAdapter;
import io.github.vfedoriv.graphrag.documents.application.inspection.StoredEmbeddingsFacade;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.service.EmbeddingSpacePolicy;

public final class AiBoundaryTestSupport {
    private AiBoundaryTestSupport() { }

    public static EmbeddingCompatibility compatibility(DocumentChunkRepository chunks) {
        return new EmbeddingCompatibility(new StoredEmbeddingInformationAdapter(new StoredEmbeddingsFacade(chunks)));
    }

    public static EmbeddingSpacePolicy bridge(DocumentChunkRepository chunks) {
        return new EmbeddingSpacePolicy(compatibility(chunks));
    }
}
