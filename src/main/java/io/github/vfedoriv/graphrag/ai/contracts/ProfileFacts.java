package io.github.vfedoriv.graphrag.ai.contracts;

import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;

import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;

/** Immutable profile execution metadata. Contains no credential or provider client. */
public record ProfileFacts(String id, long revision, String baseUrl, String chatModel, String embeddingModel,
    int embeddingDimensions, TokenizerId tokenizerId, int timeoutSeconds, int maxRetries,
    StructuredOutputMode structuredOutputMode
) {
    public ProfileFacts {
        structuredOutputMode = structuredOutputMode == null ? StructuredOutputMode.PORTABLE : structuredOutputMode;
    }

    public ProfileFacts(String id, long revision, String baseUrl, String chatModel, String embeddingModel, int embeddingDimensions, TokenizerId tokenizerId, int timeoutSeconds, int maxRetries) {
        this(id, revision, baseUrl, chatModel, embeddingModel, embeddingDimensions, tokenizerId, timeoutSeconds, maxRetries, StructuredOutputMode.PORTABLE);
    }

    public StructuredOutputMode getStructuredOutputMode() { return structuredOutputMode; }
    public String getId() { return id; }
    public long getRevision() { return revision; }
    public String getBaseUrl() { return baseUrl; }
    public String getChatModel() { return chatModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public int getEmbeddingDimensions() { return embeddingDimensions; }
    public TokenizerId getTokenizerId() { return tokenizerId; }
    public String getTokenizerIdValue() { return tokenizerId == null ? null : tokenizerId.value(); }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
}
