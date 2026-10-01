package io.github.vfedoriv.graphrag.documents.adapters.graph.repository;

import io.github.vfedoriv.graphrag.documents.adapters.graph.entity.DocumentChunkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface Neo4jDocumentChunkRepository extends Neo4jRepository<DocumentChunkEntity, String> {

    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    Optional<DocumentChunkEntity> findByIdAndDocumentId(String id, String documentId);

    @Query(
        value = """
            MATCH (chunk:DocumentChunk)
            WHERE chunk.documentId = $documentId
              AND ($kind IS NULL OR chunk.kind = $kind)
              AND ($parentChunkId IS NULL OR chunk.parentChunkId = $parentChunkId)
              AND ($sectionIndex IS NULL OR chunk.sectionIndex = $sectionIndex)
            RETURN chunk
            ORDER BY chunk.chunkIndex ASC, chunk.id ASC
            SKIP $skip LIMIT $limit
            """,
        countQuery = """
            MATCH (chunk:DocumentChunk)
            WHERE chunk.documentId = $documentId
              AND ($kind IS NULL OR chunk.kind = $kind)
              AND ($parentChunkId IS NULL OR chunk.parentChunkId = $parentChunkId)
              AND ($sectionIndex IS NULL OR chunk.sectionIndex = $sectionIndex)
            RETURN count(chunk)
            """
    )
    Page<DocumentChunkEntity> findPageByDocumentId(
        @Param("documentId") String documentId,
        @Param("kind") String kind,
        @Param("parentChunkId") String parentChunkId,
        @Param("sectionIndex") Integer sectionIndex,
        Pageable pageable
    );

    @Query(
        value = """
            MATCH (chunk:DocumentChunk)
            WHERE chunk.documentId = $documentId
              AND chunk.kind = 'CHILD'
              AND chunk.parentChunkId IS NULL
              AND ($sectionIndex IS NULL OR chunk.sectionIndex = $sectionIndex)
            RETURN chunk
            ORDER BY chunk.chunkIndex ASC, chunk.id ASC
            SKIP $skip LIMIT $limit
            """,
        countQuery = """
            MATCH (chunk:DocumentChunk)
            WHERE chunk.documentId = $documentId
              AND chunk.kind = 'CHILD'
              AND chunk.parentChunkId IS NULL
              AND ($sectionIndex IS NULL OR chunk.sectionIndex = $sectionIndex)
            RETURN count(chunk)
            """
    )
    Page<DocumentChunkEntity> findFlatPageByDocumentId(
        @Param("documentId") String documentId,
        @Param("sectionIndex") Integer sectionIndex,
        Pageable pageable
    );

    @Query(
        value = """
            MATCH (chunk:DocumentChunk)
            WHERE chunk.documentId = $documentId
              AND chunk.kind = 'PARENT'
            RETURN chunk
            ORDER BY chunk.chunkIndex ASC, chunk.id ASC
            SKIP $skip LIMIT $limit
            """,
        countQuery = """
            MATCH (chunk:DocumentChunk)
            WHERE chunk.documentId = $documentId
              AND chunk.kind = 'PARENT'
            RETURN count(chunk)
            """
    )
    Page<DocumentChunkEntity> findParentPageByDocumentId(
        @Param("documentId") String documentId,
        Pageable pageable
    );

    @Query("""
        MATCH (chunk:DocumentChunk)
        WHERE chunk.documentId = $documentId
          AND chunk.kind = 'CHILD'
          AND chunk.parentChunkId IS NULL
        RETURN count(chunk)
        """)
    long countFlatChunksByDocumentId(@Param("documentId") String documentId);

    Long deleteByDocumentId(String documentId);

    @Query("""
        MATCH (chunk:DocumentChunk {knowledgeBaseId: $knowledgeBaseId})
        WHERE (chunk.kind IS NULL OR chunk.kind = 'CHILD') AND chunk.embedding IS NOT NULL
        RETURN chunk
        """)
    List<DocumentChunkEntity> findEmbeddedChunksByKnowledgeBaseId(String knowledgeBaseId);
}
