package io.github.vfedoriv.graphrag.ai.profiles.domain;

import io.github.vfedoriv.graphrag.ai.domain.TokenizerId;
import java.time.Instant;

public class AiProfileNode {

    private String id;
    private Long version;
    private String name;
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String embeddingModel;
    private TokenizerId tokenizerId;
    private int embeddingDimensions;
    private int timeoutSeconds;
    private int maxRetries;
    private boolean defaultProfile;
    private long revision;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public TokenizerId getTokenizerId() {
        return tokenizerId;
    }

    /** Non-secret tokenizer identity for immutable capability requests. */
    public String getTokenizerIdValue() {
        return tokenizerId == null ? null : tokenizerId.value();
    }

    public void setTokenizerId(TokenizerId tokenizerId) {
        this.tokenizerId = tokenizerId;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean isDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(boolean defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    public io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts facts() {
        return new io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts(id, revision, baseUrl, chatModel,
            embeddingModel, embeddingDimensions, tokenizerId, timeoutSeconds, maxRetries);
    }

}
