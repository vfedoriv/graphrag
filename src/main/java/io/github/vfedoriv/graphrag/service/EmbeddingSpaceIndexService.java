package io.github.vfedoriv.graphrag.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmbeddingSpaceIndexService {

    private static final String INDEX_PREFIX = "document_chunk_embedding_";
    private static final String LABEL_PREFIX = "EmbeddingSpace_";
    private final Neo4jClient neo4jClient;

    public EmbeddingSpaceIndexService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public String indexName(String knowledgeBaseId, String embeddingSpaceId) {
        return INDEX_PREFIX + partitionHash(knowledgeBaseId, embeddingSpaceId);
    }

    public String labelName(String knowledgeBaseId, String embeddingSpaceId) {
        return LABEL_PREFIX + partitionHash(knowledgeBaseId, embeddingSpaceId);
    }

    public void ensureIndex(String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        String indexName = indexName(knowledgeBaseId, embeddingSpace.id());
        String labelName = labelName(knowledgeBaseId, embeddingSpace.id());
        neo4jClient.query("""
            CREATE VECTOR INDEX %s IF NOT EXISTS
            FOR (c:%s)
            ON (c.embedding)
            OPTIONS {indexConfig: {
              `vector.dimensions`: $dimensions,
              `vector.similarity_function`: 'cosine'
            }}
            """.formatted(indexName, labelName))
            .bind(embeddingSpace.dimensions()).to("dimensions")
            .run();
        log.info("Embedding-space vector index ensured: index={}, knowledgeBaseId={}, embeddingSpaceId={}",
            indexName, knowledgeBaseId, embeddingSpace.id());
    }

    public void assignChunk(String chunkId, String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        String labelName = labelName(knowledgeBaseId, embeddingSpace.id());
        neo4jClient.query("""
            MATCH (chunk:DocumentChunk {
              id: $chunkId,
              knowledgeBaseId: $knowledgeBaseId,
              embeddingSpaceId: $embeddingSpaceId
            })
            WHERE chunk.kind IS NULL OR chunk.kind = 'CHILD'
            SET chunk:%s
            """.formatted(labelName))
            .bind(chunkId).to("chunkId")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(embeddingSpace.id()).to("embeddingSpaceId")
            .run();
    }

    public long managedIndexCount() {
        Map<String, Object> row = neo4jClient.query("""
            SHOW INDEXES YIELD name, type
            WHERE type = 'VECTOR' AND name STARTS WITH $prefix
            RETURN count(*) AS count
            """)
            .bind(INDEX_PREFIX).to("prefix")
            .fetch()
            .one()
            .orElse(Map.of("count", 0L));
        Object count = row.get("count");
        return count instanceof Number number ? number.longValue() : 0L;
    }

    private String partitionHash(String knowledgeBaseId, String embeddingSpaceId) {
        return EmbeddingSpaceIdentity.sha256(knowledgeBaseId + "\n" + embeddingSpaceId).substring(0, 24);
    }
}
