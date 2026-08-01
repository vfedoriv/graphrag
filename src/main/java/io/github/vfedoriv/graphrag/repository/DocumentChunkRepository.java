package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends Neo4jRepository<DocumentChunkNode, String> {

    List<DocumentChunkNode> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    Optional<DocumentChunkNode> findByIdAndDocumentId(String id, String documentId);

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
    Page<DocumentChunkNode> findPageByDocumentId(
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
    Page<DocumentChunkNode> findParentPageByDocumentId(
        @Param("documentId") String documentId,
        Pageable pageable
    );

    @Query("""
        MATCH (chunk:DocumentChunk)
        WHERE chunk.documentId = $documentId
          AND chunk.parentChunkId IS NULL
          AND (chunk.kind IS NULL OR chunk.kind <> 'PARENT')
        RETURN count(chunk)
        """)
    long countFlatChunksByDocumentId(@Param("documentId") String documentId);

    Long deleteByDocumentId(String documentId);

    @Query("""
        MATCH (chunk:DocumentChunk {knowledgeBaseId: $knowledgeBaseId})
        WHERE (chunk.kind IS NULL OR chunk.kind = 'CHILD') AND chunk.embedding IS NOT NULL
        RETURN chunk
        """)
    List<DocumentChunkNode> findEmbeddedChunksByKnowledgeBaseId(String knowledgeBaseId);
}
