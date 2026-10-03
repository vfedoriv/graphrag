package io.github.vfedoriv.graphrag.documents.adapters.graph;

import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.documents.adapters.graph.entity.DocumentChunkEntity;
import io.github.vfedoriv.graphrag.documents.adapters.graph.repository.Neo4jDocumentChunkRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentChunkRepositoryAdapter implements DocumentChunkRepository {
    private final Neo4jDocumentChunkRepository repository;

    public DocumentChunkRepositoryAdapter(Neo4jDocumentChunkRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DocumentChunkNode> findByDocumentIdOrderByChunkIndexAsc(String documentId) {
        return repository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream().map(DocumentChunkRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<DocumentChunkNode> findByIdAndDocumentId(String id, String documentId) {
        return repository.findByIdAndDocumentId(id, documentId).map(DocumentChunkRepositoryAdapter::toDomain);
    }

    @Override
    public Page<DocumentChunkNode> findPageByDocumentId(String documentId, String kind, String parentChunkId, Integer sectionIndex, Pageable pageable) {
        return repository.findPageByDocumentId(documentId, kind, parentChunkId, sectionIndex, pageable).map(DocumentChunkRepositoryAdapter::toDomain);
    }

    @Override
    public Page<DocumentChunkNode> findFlatPageByDocumentId(String documentId, Integer sectionIndex, Pageable pageable) {
        return repository.findFlatPageByDocumentId(documentId, sectionIndex, pageable).map(DocumentChunkRepositoryAdapter::toDomain);
    }

    @Override
    public Page<DocumentChunkNode> findParentPageByDocumentId(String documentId, Pageable pageable) {
        return repository.findParentPageByDocumentId(documentId, pageable).map(DocumentChunkRepositoryAdapter::toDomain);
    }

    @Override
    public long countFlatChunksByDocumentId(String documentId) {
        return repository.countFlatChunksByDocumentId(documentId);
    }

    @Override
    public Long deleteByDocumentId(String documentId) {
        return repository.deleteByDocumentId(documentId);
    }

    @Override
    public List<DocumentChunkNode> findEmbeddedChunksByKnowledgeBaseId(String knowledgeBaseId) {
        return repository.findEmbeddedChunksByKnowledgeBaseId(knowledgeBaseId).stream().map(DocumentChunkRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<DocumentChunkNode> findById(String id) {
        return repository.findById(id).map(DocumentChunkRepositoryAdapter::toDomain);
    }

    @Override
    public List<DocumentChunkNode> findAll() {
        return repository.findAll().stream().map(DocumentChunkRepositoryAdapter::toDomain).toList();
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

    @Override
    public DocumentChunkNode save(DocumentChunkNode chunk) {
        DocumentChunkEntity entity = toEntity(chunk);
        DocumentChunkNode saved = toDomain(repository.save(entity));
        chunk.setVersion(saved.getVersion());
        return saved;
    }

    private static DocumentChunkEntity toEntity(DocumentChunkNode source) {
        DocumentChunkEntity target = new DocumentChunkEntity();
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setDocumentId(source.getDocumentId());
        target.setProcessingRunId(source.getProcessingRunId());
        target.setChunkIndex(source.getChunkIndex());
        target.setText(source.getText());
        target.setSourceText(source.getSourceText());
        target.setTokenEstimate(source.getTokenEstimate());
        target.setEmbeddingTokenCount(source.getEmbeddingTokenCount());
        target.setEmbedding(source.getEmbedding());
        target.setEmbeddingModel(source.getEmbeddingModel());
        target.setEmbeddingDimensions(source.getEmbeddingDimensions());
        target.setEmbeddingSpaceId(source.getEmbeddingSpaceId());
        target.setTokenizerId(source.getTokenizerId());
        target.setChunkStrategy(source.getChunkStrategy());
        target.setChunkStrategyRevision(source.getChunkStrategyRevision());
        target.setChunkSettingsHash(source.getChunkSettingsHash());
        target.setTokenCountMode(source.getTokenCountMode());
        target.setEffectiveChunkerRevision(source.getEffectiveChunkerRevision());
        target.setSourceStart(source.getSourceStart());
        target.setSourceEnd(source.getSourceEnd());
        target.setKind(source.getKind());
        target.setParentChunkId(source.getParentChunkId());
        target.setChildIndex(source.getChildIndex());
        target.setChildCount(source.getChildCount());
        target.setSectionIndex(source.getSectionIndex());
        target.setSectionChunkIndex(source.getSectionChunkIndex());
        target.setPageStart(source.getPageStart());
        target.setPageEnd(source.getPageEnd());
        target.setStructuralPath(source.getStructuralPath());
        target.setBlockConfidence(source.getBlockConfidence());
        target.setSourceHash(source.getSourceHash());
        target.setRepresentationRevision(source.getRepresentationRevision());
        target.setMetadata(source.getMetadata());
        return target;
    }

    private static DocumentChunkNode toDomain(DocumentChunkEntity source) {
        DocumentChunkNode target = new DocumentChunkNode();
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setKnowledgeBaseId(source.getKnowledgeBaseId());
        target.setDocumentId(source.getDocumentId());
        target.setProcessingRunId(source.getProcessingRunId());
        target.setChunkIndex(source.getChunkIndex());
        target.setText(source.getText());
        target.setSourceText(source.getSourceText());
        target.setTokenEstimate(source.getTokenEstimate());
        target.setEmbeddingTokenCount(source.getEmbeddingTokenCount());
        target.setEmbedding(source.getEmbedding());
        target.setEmbeddingModel(source.getEmbeddingModel());
        target.setEmbeddingDimensions(source.getEmbeddingDimensions());
        target.setEmbeddingSpaceId(source.getEmbeddingSpaceId());
        target.setTokenizerId(source.getTokenizerId());
        target.setChunkStrategy(source.getChunkStrategy());
        target.setChunkStrategyRevision(source.getChunkStrategyRevision());
        target.setChunkSettingsHash(source.getChunkSettingsHash());
        target.setTokenCountMode(source.getTokenCountMode());
        target.setEffectiveChunkerRevision(source.getEffectiveChunkerRevision());
        target.setSourceStart(source.getSourceStart());
        target.setSourceEnd(source.getSourceEnd());
        target.setKind(source.getKind());
        target.setParentChunkId(source.getParentChunkId());
        target.setChildIndex(source.getChildIndex());
        target.setChildCount(source.getChildCount());
        target.setSectionIndex(source.getSectionIndex());
        target.setSectionChunkIndex(source.getSectionChunkIndex());
        target.setPageStart(source.getPageStart());
        target.setPageEnd(source.getPageEnd());
        target.setStructuralPath(source.getStructuralPath());
        target.setBlockConfidence(source.getBlockConfidence());
        target.setSourceHash(source.getSourceHash());
        target.setRepresentationRevision(source.getRepresentationRevision());
        target.setMetadata(source.getMetadata());
        return target;
    }
}
