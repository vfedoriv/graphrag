package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

@Repository
public class Neo4jTextChunkRetrievalRepository implements TextChunkRetrievalRepository {

    private static final String CANDIDATE_RETURN = """
        RETURN chunk.id AS chunkId,
               chunk.documentId AS documentId,
               chunk.chunkIndex AS chunkIndex,
               chunk.sourceStart AS sourceStart,
               chunk.sourceEnd AS sourceEnd,
               chunk.pageStart AS pageStart,
               chunk.pageEnd AS pageEnd,
               chunk.processingRunId AS processingRunId,
               chunk.effectiveChunkerRevision AS effectiveChunkerRevision,
               chunk.structuralPath AS structuralPath,
               coalesce(chunk.sourceText, chunk.text) AS sourceText,
               score AS score
        ORDER BY score DESC, chunk.id ASC
        LIMIT $limit
        """;

    private final Neo4jClient neo4jClient;

    public Neo4jTextChunkRetrievalRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public List<RawCandidate> findDense(
        String indexName,
        String knowledgeBaseId,
        String embeddingSpaceId,
        List<Double> queryVector,
        int limit
    ) {
        return rows(neo4jClient.query("""
            CALL db.index.vector.queryNodes($indexName, $limit, $queryVector) YIELD node AS chunk, score
            WHERE chunk.knowledgeBaseId = $knowledgeBaseId
              AND chunk.embeddingSpaceId = $embeddingSpaceId
              AND (chunk.kind IS NULL OR chunk.kind = 'CHILD')
            """ + CANDIDATE_RETURN)
            .bind(indexName).to("indexName")
            .bind(limit).to("limit")
            .bind(queryVector).to("queryVector")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(embeddingSpaceId).to("embeddingSpaceId")
            .fetch()
            .all());
    }

    @Override
    public List<RawCandidate> findLexical(
        String indexName,
        String knowledgeBaseId,
        String luceneQuery,
        int limit
    ) {
        return rows(neo4jClient.query("""
            CALL db.index.fulltext.queryNodes($indexName, $luceneQuery, {limit: $limit})
            YIELD node AS chunk, score
            WHERE chunk.knowledgeBaseId = $knowledgeBaseId
              AND (chunk.kind IS NULL OR chunk.kind = 'CHILD')
            """ + CANDIDATE_RETURN)
            .bind(indexName).to("indexName")
            .bind(luceneQuery).to("luceneQuery")
            .bind(limit).to("limit")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .fetch()
            .all());
    }

    @Override
    public List<RawCandidate> findOwnedDocumentChunks(
        String knowledgeBaseId,
        List<String> documentIds,
        int limit
    ) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        return rows(neo4jClient.query("""
            MATCH (chunk:DocumentChunk)
            WHERE chunk.knowledgeBaseId = $knowledgeBaseId
              AND chunk.documentId IN $documentIds
              AND (chunk.kind IS NULL OR chunk.kind = 'CHILD')
            WITH chunk, 1.0 AS score
            """ + CANDIDATE_RETURN)
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(documentIds).to("documentIds")
            .bind(limit).to("limit")
            .fetch()
            .all());
    }

    private List<RawCandidate> rows(Iterable<Map<String, Object>> rows) {
        List<RawCandidate> candidates = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            SourceIdentity source = new SourceIdentity(
                stringValue(row.get("chunkId")),
                stringValue(row.get("documentId")),
                intValue(row.get("chunkIndex")),
                nullableIntValue(row.get("sourceStart")),
                nullableIntValue(row.get("sourceEnd")),
                nullableIntValue(row.get("pageStart")),
                nullableIntValue(row.get("pageEnd")),
                stringValue(row.get("processingRunId")),
                stringValue(row.get("effectiveChunkerRevision")),
                stringValue(row.get("structuralPath"))
            );
            candidates.add(new RawCandidate(source, doubleValue(row.get("score")), stringValue(row.get("sourceText"))));
        }
        return List.copyOf(candidates);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Integer nullableIntValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
