package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.repository.LexicalIndexRepository;
import io.github.vfedoriv.graphrag.service.LexicalIndexIdentity;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class Neo4jLexicalIndexRepository implements LexicalIndexRepository {

    private static final long READINESS_POLL_NANOS = 10_000_000L;
    private final Neo4jClient neo4jClient;
    private final Driver driver;

    public Neo4jLexicalIndexRepository(Neo4jClient neo4jClient, Driver driver) {
        this.neo4jClient = neo4jClient;
        this.driver = driver;
    }

    @Override
    public String indexName(String knowledgeBaseId) {
        return LexicalIndexIdentity.indexName(knowledgeBaseId);
    }

    @Override
    public String labelName(String knowledgeBaseId) {
        return LexicalIndexIdentity.labelName(knowledgeBaseId);
    }

    @Override
    public void assignChild(String chunkId, String knowledgeBaseId) {
        neo4jClient.query("""
            MATCH (chunk:DocumentChunk {id: $chunkId, knowledgeBaseId: $knowledgeBaseId})
            WHERE chunk.kind IS NULL OR chunk.kind = 'CHILD'
            SET chunk:%s,
                chunk.sourceText = coalesce(chunk.sourceText, chunk.text)
            """.formatted(labelName(knowledgeBaseId)))
            .bind(chunkId).to("chunkId")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .run();
    }

    @Override
    public void ensureOnline(String knowledgeBaseId, Instant deadline) {
        requireTime(deadline);
        String labelName = labelName(knowledgeBaseId);
        String indexName = indexName(knowledgeBaseId);
        neo4jClient.query("""
            MATCH (chunk:DocumentChunk {knowledgeBaseId: $knowledgeBaseId})
            WHERE chunk.kind IS NULL OR chunk.kind = 'CHILD'
            SET chunk:%s,
                chunk.sourceText = coalesce(chunk.sourceText, chunk.text)
            """.formatted(labelName))
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .run();
        requireTime(deadline);
        neo4jClient.query("""
            CREATE FULLTEXT INDEX %s IF NOT EXISTS
            FOR (chunk:%s) ON EACH [chunk.sourceText]
            OPTIONS {indexConfig: {
              `fulltext.analyzer`: 'standard-no-stop-words',
              `fulltext.eventually_consistent`: false
            }}
            """.formatted(indexName, labelName)).run();
        while (Instant.now().isBefore(deadline)) {
            Map<String, Object> row = neo4jClient.query("""
                SHOW FULLTEXT INDEXES YIELD name, state
                WHERE name = $indexName
                RETURN state
                """)
                .bind(indexName).to("indexName")
                .fetch()
                .one()
                .orElse(Map.of());
            if ("ONLINE".equals(String.valueOf(row.get("state")))) {
                log.info("Knowledge-base lexical index online: index={}, knowledgeBaseId={}", indexName, knowledgeBaseId);
                return;
            }
            LockSupport.parkNanos(READINESS_POLL_NANOS);
        }
        throw new IllegalStateException("Lexical index readiness deadline exceeded");
    }

    @Override
    public void drop(String knowledgeBaseId) {
        String indexName = indexName(knowledgeBaseId);
        String query = "DROP INDEX %s IF EXISTS".formatted(indexName);
        try (Session session = driver.session()) {
            session.run(query).consume();
        }
        log.info("Knowledge-base lexical index dropped: index={}, knowledgeBaseId={}", indexName, knowledgeBaseId);
    }

    private void requireTime(Instant deadline) {
        if (deadline == null || !Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("Lexical index readiness deadline exceeded");
        }
    }
}
