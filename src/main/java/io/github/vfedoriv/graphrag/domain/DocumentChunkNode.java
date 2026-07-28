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

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
