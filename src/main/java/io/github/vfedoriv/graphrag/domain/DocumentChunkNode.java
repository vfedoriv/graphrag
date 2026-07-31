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
    private String processingRunId;
    private int chunkIndex;
    private String text;
    private int tokenEstimate;
    private int embeddingTokenCount;
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
    private String kind;
    private String parentChunkId;
    private Integer childIndex;
    private int childCount;
    private int sectionIndex;
    private int sectionChunkIndex;
    private Integer pageStart;
    private Integer pageEnd;
    private String structuralPath;
    private String blockConfidence;
    private String sourceHash;
    private String representationRevision;
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

    public String getProcessingRunId() {
        return processingRunId;
    }

    public void setProcessingRunId(String processingRunId) {
        this.processingRunId = processingRunId;
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

    public int getEmbeddingTokenCount() {
        return embeddingTokenCount;
    }

    public void setEmbeddingTokenCount(int embeddingTokenCount) {
        this.embeddingTokenCount = embeddingTokenCount;
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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getParentChunkId() {
        return parentChunkId;
    }

    public void setParentChunkId(String parentChunkId) {
        this.parentChunkId = parentChunkId;
    }

    public Integer getChildIndex() {
        return childIndex;
    }

    public void setChildIndex(Integer childIndex) {
        this.childIndex = childIndex;
    }

    public int getChildCount() {
        return childCount;
    }

    public void setChildCount(int childCount) {
        this.childCount = childCount;
    }

    public int getSectionIndex() {
        return sectionIndex;
    }

    public void setSectionIndex(int sectionIndex) {
        this.sectionIndex = sectionIndex;
    }

    public int getSectionChunkIndex() {
        return sectionChunkIndex;
    }

    public void setSectionChunkIndex(int sectionChunkIndex) {
        this.sectionChunkIndex = sectionChunkIndex;
    }

    public Integer getPageStart() {
        return pageStart;
    }

    public void setPageStart(Integer pageStart) {
        this.pageStart = pageStart;
    }

    public Integer getPageEnd() {
        return pageEnd;
    }

    public void setPageEnd(Integer pageEnd) {
        this.pageEnd = pageEnd;
    }

    public String getStructuralPath() {
        return structuralPath;
    }

    public void setStructuralPath(String structuralPath) {
        this.structuralPath = structuralPath;
    }

    public String getBlockConfidence() {
        return blockConfidence;
    }

    public void setBlockConfidence(String blockConfidence) {
        this.blockConfidence = blockConfidence;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    public String getRepresentationRevision() {
        return representationRevision;
    }

    public void setRepresentationRevision(String representationRevision) {
        this.representationRevision = representationRevision;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
