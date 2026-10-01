package io.github.vfedoriv.graphrag.documents.ports;

import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DocumentChunkRepository {

    List<DocumentChunkNode> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    Optional<DocumentChunkNode> findByIdAndDocumentId(String id, String documentId);

    Page<DocumentChunkNode> findPageByDocumentId(
        String documentId,
        String kind,
        String parentChunkId,
        Integer sectionIndex,
        Pageable pageable
    );

    Page<DocumentChunkNode> findFlatPageByDocumentId(
        String documentId,
        Integer sectionIndex,
        Pageable pageable
    );

    Page<DocumentChunkNode> findParentPageByDocumentId(
        String documentId,
        Pageable pageable
    );

    long countFlatChunksByDocumentId(String documentId);

    Long deleteByDocumentId(String documentId);

    List<DocumentChunkNode> findEmbeddedChunksByKnowledgeBaseId(String knowledgeBaseId);
    DocumentChunkNode save(DocumentChunkNode chunk);
    Optional<DocumentChunkNode> findById(String id);
    List<DocumentChunkNode> findAll();
    void deleteAll();
}
