package io.github.vfedoriv.graphrag.infrastructure.persistence.graph;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GraphSchemaInitializer implements ApplicationRunner {

    private static final List<String> STATEMENTS = List.of(
        "CREATE CONSTRAINT document_chunk_id IF NOT EXISTS FOR (c:DocumentChunk) REQUIRE c.id IS UNIQUE",
        "CREATE CONSTRAINT graph_extraction_evidence_id IF NOT EXISTS "
            + "FOR (e:GraphExtractionEvidence) REQUIRE e.id IS UNIQUE",
        "CREATE INDEX document_chunk_knowledge_base IF NOT EXISTS "
            + "FOR (c:DocumentChunk) ON (c.knowledgeBaseId)",
        "CREATE INDEX document_chunk_document IF NOT EXISTS "
            + "FOR (c:DocumentChunk) ON (c.documentId)",
        "CREATE INDEX document_chunk_hierarchy IF NOT EXISTS "
            + "FOR (c:DocumentChunk) ON (c.documentId, c.kind, c.parentChunkId)",
        "CREATE INDEX document_chunk_scope_space IF NOT EXISTS "
            + "FOR (c:DocumentChunk) ON (c.knowledgeBaseId, c.embeddingSpaceId)",
        "CREATE INDEX graph_extraction_evidence_knowledge_base IF NOT EXISTS "
            + "FOR (e:GraphExtractionEvidence) ON (e.knowledgeBaseId)",
        "CREATE INDEX graph_extraction_evidence_document IF NOT EXISTS "
            + "FOR (e:GraphExtractionEvidence) ON (e.sourceDocumentId)",
        "CREATE INDEX graph_extraction_evidence_run IF NOT EXISTS "
            + "FOR (e:GraphExtractionEvidence) ON (e.extractionRunId)",
        "CREATE INDEX graph_extraction_evidence_fact IF NOT EXISTS "
            + "FOR (e:GraphExtractionEvidence) ON (e.canonicalFactId)",
        "CREATE INDEX graph_extraction_evidence_chunk IF NOT EXISTS "
            + "FOR (e:GraphExtractionEvidence) ON (e.sourceChunkId)"
    );

    private final Neo4jClient neo4jClient;

    public GraphSchemaInitializer(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        STATEMENTS.forEach(statement -> neo4jClient.query(statement).run());
    }
}
