package io.github.vfedoriv.graphrag.documents.adapters.relational;

import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.adapters.relational.repository.JpaDocumentUploadRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalDocumentUploadRepository implements DocumentUploadRepository {
    private final JpaDocumentUploadRepository repository;

    public RelationalDocumentUploadRepository(JpaDocumentUploadRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DocumentUploadNode> findByKnowledgeBaseIdAndSha256(String knowledgeBaseId, String sha256) {
        return repository.findByKnowledgeBaseIdAndSha256(knowledgeBaseId, sha256)
            .map(DocumentWorkflowRelationalMapper::toDomain);
    }

    @Override
    public List<DocumentUploadNode> findByKnowledgeBaseIdOrderByUploadedAtDesc(String knowledgeBaseId) {
        return repository.findByKnowledgeBaseIdOrderByUploadedAtDescIdDesc(knowledgeBaseId).stream()
            .map(DocumentWorkflowRelationalMapper::toDomain)
            .toList();
    }

    @Override
    public Optional<DocumentUploadNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId) {
        return repository.findByIdAndKnowledgeBaseId(id, knowledgeBaseId)
            .map(DocumentWorkflowRelationalMapper::toDomain);
    }

    @Override
    public List<DocumentUploadNode> findAllByIdInAndKnowledgeBaseId(List<String> ids, String knowledgeBaseId) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIdInAndKnowledgeBaseId(ids, knowledgeBaseId).stream()
            .map(DocumentWorkflowRelationalMapper::toDomain)
            .toList();
    }

    @Override
    public List<DocumentUploadNode> findByMetadata(
        String knowledgeBaseId,
        String originalFilename,
        String contentType,
        int limit
    ) {
        String filenameFilter = originalFilename == null || originalFilename.isBlank() ? null : originalFilename.strip();
        String contentTypeFilter = contentType == null || contentType.isBlank() ? null : contentType.strip();
        return repository.findByMetadata(
            knowledgeBaseId,
            filenameFilter,
            contentTypeFilter,
            PageRequest.of(0, limit)
        ).stream().map(DocumentWorkflowRelationalMapper::toDomain).toList();
    }

    @Override
    public long countByKnowledgeBaseId(String knowledgeBaseId) {
        return repository.countByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public Page<DocumentUploadNode> findPageByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable) {
        Pageable deterministicPageable = pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"))
            );
        return repository.findByKnowledgeBaseId(knowledgeBaseId, deterministicPageable)
            .map(DocumentWorkflowRelationalMapper::toDomain);
    }

    @Override
    public List<DocumentUploadNode> findAll() {
        return repository.findAll().stream().map(DocumentWorkflowRelationalMapper::toDomain).toList();
    }

    @Override
    public Optional<DocumentUploadNode> findById(String id) {
        return repository.findById(id).map(DocumentWorkflowRelationalMapper::toDomain);
    }

    @Override
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    @Override
    public DocumentUploadNode save(DocumentUploadNode document) {
        return DocumentWorkflowRelationalMapper.toDomain(
            repository.saveAndFlush(DocumentWorkflowRelationalMapper.toEntity(document))
        );
    }

    @Override
    public void delete(DocumentUploadNode document) {
        repository.deleteById(document.getId());
        repository.flush();
    }
}
