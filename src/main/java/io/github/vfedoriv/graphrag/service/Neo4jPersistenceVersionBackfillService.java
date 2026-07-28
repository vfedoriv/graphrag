package io.github.vfedoriv.graphrag.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class Neo4jPersistenceVersionBackfillService implements ApplicationRunner {

    private final Neo4jClient neo4jClient;

    public Neo4jPersistenceVersionBackfillService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        long versionBackfilled = executeCount("""
            MATCH (node)
            WHERE (
                node:DocumentChunk OR
                node:DocumentProcessingRun OR
                node:DocumentUpload OR
                node:ExtractionRun
            )
            AND node.version IS NULL
            SET node.version = 0
            RETURN count(node) AS count
            """);
        long draftBackfilled = executeCount("""
            MATCH (node)
            WHERE (
                node:SchemaDraft OR
                node:SchemaDraftSource OR
                node:SchemaDraftSourceRevision OR
                node:SchemaDraftAnalysisRun OR
                node:SchemaDraftSourceResult OR
                node:SchemaDraftAggregateRevision OR
                node:SchemaDraftDecision OR
                node:SchemaDraftConflict OR
                node:SchemaDraftStorageMutation OR
                node:SchemaDraftEvaluationRun OR
                node:SchemaDraftEvaluationOutcome OR
                node:SchemaDraftPublication OR
                node:SchemaReprocessingPlan OR
                node:SchemaReprocessingItem
            )
            AND node.persistenceVersion IS NULL
            SET node.persistenceVersion = 0
            RETURN count(node) AS count
            """);
        if (versionBackfilled > 0 || draftBackfilled > 0) {
            log.info(
                "Backfilled Neo4j persistence version metadata: versionNodes={}, schemaDraftNodes={}",
                versionBackfilled,
                draftBackfilled
            );
        }
    }

    private long executeCount(String cypher) {
        Map<String, Object> row = neo4jClient.query(cypher)
            .fetch()
            .one()
            .orElse(Map.of("count", 0L));
        Object count = row.get("count");
        if (count instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
