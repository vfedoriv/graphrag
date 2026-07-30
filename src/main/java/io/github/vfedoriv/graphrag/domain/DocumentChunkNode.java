package io.github.vfedoriv.graphrag.domain;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("DocumentChunk")
public class DocumentChunkNode {

    @Id
    private String id;
    @Version
    private Long version;
    private String knowledgeBaseId;
    private String documentId;
    private int chunkIndex;
    private String text;
    private int tokenEstimate;
    private List<Double> embedding;
    private String embeddingModel;
    private int embeddingDimensions;
    private String embeddingSpaceId;
    private String tokenizerId;
    private String chunkStrategy;
    private String chunkStrategyRevision;
    private String chunkSettingsHash;
    private String tokenCountMode;
    private String effectiveChunkerRevision;
    private Integer sourceStart;
    private Integer sourceEnd;
    private String metadata;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public void setTokenEstimate(int tokenEstimate) {
        this.tokenEstimate = tokenEstimate;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public String getEmbeddingSpaceId() {
        return embeddingSpaceId;
    }

    public void setEmbeddingSpaceId(String embeddingSpaceId) {
        this.embeddingSpaceId = embeddingSpaceId;
    }

    public String getTokenizerId() {
        return tokenizerId;
    }

    public void setTokenizerId(String tokenizerId) {
        this.tokenizerId = tokenizerId;
    }

    public String getChunkStrategy() {
        return chunkStrategy;
    }

    public void setChunkStrategy(String chunkStrategy) {
        this.chunkStrategy = chunkStrategy;
    }

    public String getChunkStrategyRevision() {
        return chunkStrategyRevision;
    }

    public void setChunkStrategyRevision(String chunkStrategyRevision) {
        this.chunkStrategyRevision = chunkStrategyRevision;
    }

    public String getChunkSettingsHash() {
        return chunkSettingsHash;
    }

    public void setChunkSettingsHash(String chunkSettingsHash) {
        this.chunkSettingsHash = chunkSettingsHash;
    }

    public String getTokenCountMode() {
        return tokenCountMode;
    }

    public void setTokenCountMode(String tokenCountMode) {
        this.tokenCountMode = tokenCountMode;
    }

    public String getEffectiveChunkerRevision() {
        return effectiveChunkerRevision;
    }

    public void setEffectiveChunkerRevision(String effectiveChunkerRevision) {
        this.effectiveChunkerRevision = effectiveChunkerRevision;
    }

    public Integer getSourceStart() {
        return sourceStart;
    }

    public void setSourceStart(Integer sourceStart) {
        this.sourceStart = sourceStart;
    }

    public Integer getSourceEnd() {
        return sourceEnd;
    }

    public void setSourceEnd(Integer sourceEnd) {
        this.sourceEnd = sourceEnd;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
