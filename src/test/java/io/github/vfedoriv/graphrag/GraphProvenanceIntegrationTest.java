package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.graph.GraphWriteService;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.service.GraphArtifactCleanupService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@IntegrationTest
class GraphProvenanceIntegrationTest {

    private static final String SCHEMA_ID = "schema-provenance";
    private static final SchemaDocument SCHEMA = new SchemaDocument(
        "provenance",
        1,
        "",
        List.of(
            new SchemaDocument.NodeDefinition("Contract", "", List.of("contractId"), List.of(new SchemaDocument.PropertyDefinition("contractId", "string", true))),
            new SchemaDocument.NodeDefinition("Party", "", List.of("partyId"), List.of(new SchemaDocument.PropertyDefinition("partyId", "string", true)))
        ),
        List.of(new SchemaDocument.RelationshipDefinition("HAS_PARTY", "Contract", "Party", "", List.of(new SchemaDocument.PropertyDefinition("role", "string", false)))),
        List.of(),
        List.of()
    );

    @Autowired
    private GraphWriteService graphWriteService;
    @Autowired
    private GraphArtifactCleanupService graphArtifactCleanupService;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetIntegrationState() throws Exception {
        IntegrationTestLifecycle.reset(jdbcTemplate, neo4jClient);
    }

    @Test
    void deletingEitherDocumentRetainsCanonicalFactsSupportedByTheOtherDocument() {
        createDocumentRunChunk("doc-a", "run-a", "chunk-a", "COMPLETED");
        createDocumentRunChunk("doc-b", "run-b", "chunk-b", "COMPLETED");
        writeSharedFact("doc-a", "run-a", "chunk-a", "supplier-a");
        writeSharedFact("doc-b", "run-b", "chunk-b", "supplier-b");

        assertThat(count("MATCH (e:GraphExtractionEvidence) RETURN count(e) AS count")).isEqualTo(6L);
        assertThat(count("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS count")).isEqualTo(1L);

        graphArtifactCleanupService.cleanupDocumentArtifacts("doc-a");

        assertThat(count("MATCH (e:GraphExtractionEvidence {sourceDocumentId: 'doc-a'}) RETURN count(e) AS count")).isZero();
        assertThat(count("MATCH (e:GraphExtractionEvidence {sourceDocumentId: 'doc-b'}) RETURN count(e) AS count")).isEqualTo(3L);
        assertThat(count("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS count")).isEqualTo(1L);
        assertThat(count("MATCH (:Contract) RETURN count(*) AS count")).isEqualTo(1L);
        assertThat(count("MATCH (:Party) RETURN count(*) AS count")).isEqualTo(1L);

        graphArtifactCleanupService.cleanupDocumentArtifacts("doc-b");

        assertThat(count("MATCH (e:GraphExtractionEvidence) RETURN count(e) AS count")).isZero();
        assertThat(count("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS count")).isZero();
        assertThat(count("MATCH (:Contract) RETURN count(*) AS count")).isZero();
        assertThat(count("MATCH (:Party) RETURN count(*) AS count")).isZero();
    }

    @Test
    void runScopedCleanupRemovesOnlyItsEvidenceWhenAnotherDocumentSupportsTheFact() {
        createDocumentRunChunk("doc-a", "run-failed", "chunk-failed", "FAILED");
        createDocumentRunChunk("doc-a", "run-current", "chunk-current", "COMPLETED");
        createDocumentRunChunk("doc-a", "run-stale", "chunk-stale", "COMPLETED");
        createDocumentRunChunk("doc-a", "run-overwrite", "chunk-overwrite", "COMPLETED");
        createDocumentRunChunk("doc-b", "run-b", "chunk-b", "COMPLETED");
        writeSharedFact("doc-a", "run-failed", "chunk-failed", "failed");
        writeSharedFact("doc-a", "run-stale", "chunk-stale", "stale");
        writeSharedFact("doc-b", "run-b", "chunk-b", "retained");

        graphArtifactCleanupService.cleanupExtractionRuns("doc-a", List.of("run-failed"));

        assertThat(count("MATCH (:GraphExtractionEvidence {extractionRunId: 'run-failed'}) RETURN count(*) AS count")).isZero();
        assertThat(count("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS count")).isEqualTo(1L);

        graphArtifactCleanupService.cleanupExtractionRuns("doc-a", List.of("run-current", "run-stale"));

        assertThat(count("MATCH (:GraphExtractionEvidence {extractionRunId: 'run-stale'}) RETURN count(*) AS count")).isZero();
        assertThat(count("MATCH (:GraphExtractionEvidence {sourceDocumentId: 'doc-b'}) RETURN count(*) AS count")).isEqualTo(3L);
        assertThat(count("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS count")).isEqualTo(1L);
    }

    @Test
    void graphWritesRequireConsistentScopeAndCreateNoOperationalAnchors() {
        createDocumentRunChunk("doc-a", "run-a", "chunk-a", "COMPLETED");

        assertThatThrownBy(() -> graphWriteService.write(
            "other-kb",
            "run-a",
            SCHEMA_ID,
            "doc-a",
            "chunk-a",
            SCHEMA,
            new GraphExtractionResult(List.of(), List.of())
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Chunk scope");

        writeSharedFact("doc-a", "run-a", "chunk-a", "supplier");

        assertThat(count("""
            MATCH (e:GraphExtractionEvidence {
              knowledgeBaseId: 'kb-provenance',
              sourceDocumentId: 'doc-a',
              extractionRunId: 'run-a'
            })
            RETURN count(e) AS count
            """)).isEqualTo(3L);
        assertThat(count("""
            MATCH (n)
            WHERE n:KnowledgeBase OR n:DocumentUpload OR n:ExtractionRun OR n:DocumentProcessingRun
            RETURN count(n) AS count
            """)).isZero();
    }

    private void createDocumentRunChunk(String documentId, String runId, String chunkId, String status) {
        neo4jClient.query("""
            CREATE (:DocumentChunk {
              id: $chunkId,
              knowledgeBaseId: 'kb-provenance',
              documentId: $documentId
            })
            """)
            .bind(documentId).to("documentId")
            .bind(chunkId).to("chunkId")
            .run();
    }

    private void writeSharedFact(String documentId, String runId, String chunkId, String role) {
        graphWriteService.write(
            "kb-provenance",
            runId,
            SCHEMA_ID,
            documentId,
            chunkId,
            SCHEMA,
            new GraphExtractionResult(
                List.of(
                    new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-SHARED"), 0.9),
                    new GraphExtractionResult.ExtractedNode("Party", Map.of("partyId", "P-SHARED"), 0.9)
                ),
                List.of(new GraphExtractionResult.ExtractedRelationship(
                    "HAS_PARTY",
                    "Contract",
                    Map.of("contractId", "C-SHARED"),
                    "Party",
                    Map.of("partyId", "P-SHARED"),
                    Map.of("role", role),
                    0.9
                ))
            )
        );
    }

    private long count(String cypher) {
        return neo4jClient.query(cypher).fetchAs(Long.class).one().orElse(0L);
    }

}
