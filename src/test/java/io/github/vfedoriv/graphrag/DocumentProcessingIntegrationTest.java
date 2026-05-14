package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.service.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DocumentProcessingIntegrationTest.FakeEmbeddingConfig.class})
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration,"
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "app.storage.documents-root=./target/test-documents"
})
class DocumentProcessingIntegrationTest {

    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentProcessingService documentProcessingService;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private SchemaRegistryService schemaRegistryService;

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void persistsChunksCreatesVectorIndexAndSupportsVectorSearch() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        String schemaJson = """
            {
              "name": "contracts",
              "version": 1,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                },
                {
                  "label": "Party",
                  "key": "name",
                  "properties": [{"name": "name", "type": "string"}]
                }
              ],
              "relationships": [
                {"type": "HAS_PARTY", "from": "Contract", "to": "Party"}
              ]
            }
            """;
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-1", schema.getId());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "contract.txt",
            "text/plain",
            "first chunk sentence. second chunk sentence.".getBytes()
        );
        DocumentUploadNode uploaded = documentUploadService.upload("kb-1", file);
        DocumentUploadNode processed = documentProcessingService.process(uploaded.getId());
        DocumentUploadNode processedAgain = documentProcessingService.process(uploaded.getId());

        assertThat(processed.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(processedAgain.getStatus().name()).isEqualTo("COMPLETED");
        List<DocumentChunkNode> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).extracting("chunkIndex").isSorted();
        assertThat(chunks.get(0).getEmbedding()).hasSize(1536);

        Long indexCount = neo4jClient.query("""
            SHOW INDEXES YIELD name, type
            WHERE name = $name AND type = 'VECTOR'
            RETURN count(*) AS c
            """)
            .bind(DocumentProcessingService.CHUNK_EMBEDDING_INDEX).to("name")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(indexCount).isEqualTo(1L);

        Long hitCount = neo4jClient.query("""
            CALL db.index.vector.queryNodes($name, 3, $queryVector) YIELD node, score
            RETURN count(node) AS c
            """)
            .bind(DocumentProcessingService.CHUNK_EMBEDDING_INDEX).to("name")
            .bind(vectorOf(0.11)).to("queryVector")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(hitCount).isGreaterThan(0L);

        Long anyContract = neo4jClient.query("MATCH (n:Contract) RETURN count(n) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        Long anyParty = neo4jClient.query("MATCH (n:Party) RETURN count(n) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        Long relCount = neo4jClient.query("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(anyContract).isEqualTo(1L);
        assertThat(anyParty).isEqualTo(1L);
        assertThat(relCount).isEqualTo(1L);

        Long provenanceOnNode = neo4jClient.query("""
            MATCH (n:Contract)
            WHERE n.sourceDocumentId = $documentId AND n.schemaId IS NOT NULL AND n.extractionRunId IS NOT NULL
            RETURN count(n) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long provenanceOnRel = neo4jClient.query("""
            MATCH (:Contract)-[r:HAS_PARTY]->(:Party)
            WHERE r.sourceDocumentId = $documentId AND r.schemaId IS NOT NULL AND r.extractionRunId IS NOT NULL
            RETURN count(r) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long extractionRuns = neo4jClient.query("""
            MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(r:ExtractionRun)
            RETURN count(r) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(provenanceOnNode).isEqualTo(1L);
        assertThat(provenanceOnRel).isEqualTo(1L);
        assertThat(extractionRuns).isEqualTo(2L);
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> {
                List<List<Double>> out = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    out.add(vectorOf(0.11 + i));
                }
                return out;
            };
        }

        @Bean
        GraphExtractionClient graphExtractionClient() {
            return (schema, chunkText) -> new GraphExtractionResult(
                List.of(
                    new GraphExtractionResult.ExtractedNode(
                        "Contract",
                        java.util.Map.of("contractId", "C-1"),
                        0.95
                    ),
                    new GraphExtractionResult.ExtractedNode(
                        "Party",
                        java.util.Map.of("name", "Acme"),
                        0.90
                    )
                ),
                List.of(
                    new GraphExtractionResult.ExtractedRelationship(
                        "HAS_PARTY",
                        "Contract",
                        java.util.Map.of("contractId", "C-1"),
                        "Party",
                        java.util.Map.of("name", "Acme"),
                        java.util.Map.of("role", "Supplier"),
                        0.88
                    )
                )
            );
        }
    }

    private static List<Double> vectorOf(double base) {
        List<Double> vector = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) {
            vector.add(base + (i * 0.000001));
        }
        return vector;
    }

}
