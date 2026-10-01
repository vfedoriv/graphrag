package io.github.vfedoriv.graphrag;

import static io.github.vfedoriv.graphrag.document.StructuredDocumentTestFixtures.docxBytes;
import static io.github.vfedoriv.graphrag.document.StructuredDocumentTestFixtures.pdfPageWithLines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.CreatePlanRequest;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.documents.ports.GraphExtractionClient;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.documents.ports.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIdentity;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.repository.LexicalIndexRepository;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import io.github.vfedoriv.graphrag.service.SchemaReprocessingPlanService;
import io.github.vfedoriv.graphrag.documents.application.processing.ChunkingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
@IntegrationTest
class DocumentProcessingIntegrationTest {

    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentProcessingService documentProcessingService;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private ExtractionRunRepository extractionRunRepository;
    @Autowired
    private DocumentProcessingRunRepository processingRunRepository;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private EmbeddingSpaceIndexService embeddingSpaceIndexService;
    @Autowired
    private LexicalIndexRepository lexicalIndexRepository;
    @Autowired
    private SchemaReprocessingPlanService reprocessingService;
    @Autowired
    private SchemaReprocessingPlanRepository planRepository;
    @Autowired
    private ChunkingService chunkingService;

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void persistsChunksCreatesVectorIndexAndSupportsVectorSearch() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
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
        List<String> firstChunkIds = documentChunkRepository
            .findByDocumentIdOrderByChunkIndexAsc(uploaded.getId())
            .stream()
            .map(DocumentChunkNode::getId)
            .toList();
        Throwable secondAttempt = catchThrowable(() -> documentProcessingService.process(uploaded.getId()));
        DocumentUploadNode processedAgain = documentProcessingService.process(uploaded.getId(), true);

        assertThat(processed.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(secondAttempt).isInstanceOf(ConflictException.class);
        assertThat(processedAgain.getStatus().name()).isEqualTo("COMPLETED");
        List<DocumentChunkNode> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
        List<DocumentChunkNode> parents = chunks.stream()
            .filter(chunk -> "PARENT".equals(chunk.getKind()))
            .toList();
        List<DocumentChunkNode> children = chunks.stream()
            .filter(chunk -> "CHILD".equals(chunk.getKind()))
            .toList();
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).extracting("chunkIndex").isSorted();
        assertThat(parents).isNotEmpty();
        assertThat(children).isNotEmpty();
        assertThat(parents).allSatisfy(parent -> {
            assertThat(parent.getEmbedding()).isNull();
            assertThat(parent.getEmbeddingSpaceId()).isNull();
            assertThat(parent.getChildCount()).isPositive();
            assertThat(parent.getSourceHash()).matches("[0-9a-f]{64}");
        });
        assertThat(children).allSatisfy(child -> {
            assertThat(child.getEmbedding()).hasSize(1536);
            assertThat(child.getParentChunkId()).isNotBlank();
            assertThat(child.getChildIndex()).isNotNull();
            assertThat(child.getSourceText()).isEqualTo(child.getText());
        });
        EmbeddingSpace embeddingSpace = EmbeddingSpaceIdentity.derive(
            "https://api.openai.com/v1", "text-embedding-3-small", 1536
        );
        assertThat(children).extracting(DocumentChunkNode::getEmbeddingSpaceId)
            .containsOnly(embeddingSpace.id());
        assertThat(chunks).extracting(DocumentChunkNode::getChunkStrategy)
            .containsOnly("recursive");
        assertThat(chunks).extracting(DocumentChunkNode::getTokenizerId)
            .containsOnly("cl100k_base");
        assertThat(chunks).extracting(DocumentChunkNode::getTokenCountMode)
            .containsOnly("EXACT");
        assertThat(chunks).extracting(DocumentChunkNode::getEffectiveChunkerRevision)
            .allMatch(revision -> revision != null && revision.startsWith("chunker_"));
        assertThat(chunks).extracting(DocumentChunkNode::getSourceStart)
            .allMatch(position -> position != null && position >= 0);
        assertThat(chunks).extracting(DocumentChunkNode::getId)
            .containsExactlyElementsOf(firstChunkIds);
        assertThat(chunks).extracting(DocumentChunkNode::getKind).contains("PARENT", "CHILD");
        assertThat(chunks).extracting(DocumentChunkNode::getSectionIndex).containsOnly(0);
        assertThat(children).extracting(DocumentChunkNode::getSectionChunkIndex).containsExactly(0);
        assertThat(chunks).extracting(DocumentChunkNode::getSourceHash)
            .allMatch(hash -> hash != null && hash.matches("[0-9a-f]{64}"));
        assertThat(chunks).extracting(DocumentChunkNode::getRepresentationRevision)
            .containsOnly("context-header-v1");
        assertThat(chunks).extracting(DocumentChunkNode::getText)
            .allMatch(text -> !text.contains("context-header-v1"));
        Long orphanChildren = neo4jClient.query("""
            MATCH (child:DocumentChunk {documentId: $documentId, kind: 'CHILD'})
            WHERE NOT EXISTS {
                MATCH (:DocumentChunk {id: child.parentChunkId, kind: 'PARENT'})-[:HAS_CHILD]->(child)
            }
            RETURN count(child) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(orphanChildren).isZero();
        List<DocumentProcessingRunNode> runHistory =
            processingRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId());
        assertThat(runHistory).hasSize(2);
        assertThat(runHistory).extracting(DocumentProcessingRunNode::getTokenizerId)
            .containsOnly("cl100k_base");
        assertThat(runHistory).extracting(DocumentProcessingRunNode::getEffectiveChunkerRevision)
            .allMatch(revision -> revision != null && revision.startsWith("chunker_"));

        Long indexCount = neo4jClient.query("""
            SHOW INDEXES YIELD name, type
            WHERE name = $name AND type = 'VECTOR'
            RETURN count(*) AS c
            """)
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(indexCount).isEqualTo(1L);

        Long hitCount = neo4jClient.query("""
            CALL db.index.vector.queryNodes($name, 3, $queryVector) YIELD node, score
            RETURN count(node) AS c
            """)
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .bind(vectorOf(0.11)).to("queryVector")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(hitCount).isGreaterThan(0L);
        Long parentVectorHits = neo4jClient.query("""
            CALL db.index.vector.queryNodes($name, 10, $queryVector) YIELD node, score
            WHERE node.kind = 'PARENT'
            RETURN count(node) AS c
            """)
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .bind(vectorOf(0.11)).to("queryVector")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(parentVectorHits).isZero();
        Long lexicalChildren = neo4jClient.query("""
            MATCH (chunk:DocumentChunk:%s {knowledgeBaseId: 'kb-1', kind: 'CHILD'})
            WHERE chunk.sourceText = chunk.text
            RETURN count(chunk) AS count
            """.formatted(lexicalIndexRepository.labelName("kb-1")))
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(lexicalChildren).isEqualTo((long) children.size());

        Long anyContract = neo4jClient.query("MATCH (n:Contract) RETURN count(n) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        Long anyParty = neo4jClient.query("MATCH (n:Party) RETURN count(n) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        Long relCount = neo4jClient.query("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(anyContract).isEqualTo(1L);
        assertThat(anyParty).isEqualTo(1L);
        assertThat(relCount).isEqualTo(1L);

        Long nodeEvidence = neo4jClient.query("""
            MATCH (e:NodeExtractionEvidence {sourceDocumentId: $documentId})-[:ASSERTS_NODE]->(:Contract)
            WHERE e.schemaId IS NOT NULL AND e.extractionRunId IS NOT NULL
            RETURN count(e) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long relationshipEvidence = neo4jClient.query("""
            MATCH (e:RelationshipExtractionEvidence {sourceDocumentId: $documentId})
            WHERE e.schemaId IS NOT NULL AND e.extractionRunId IS NOT NULL
            RETURN count(e) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        int extractionRuns = extractionRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId()).size();
        int processingRuns = processingRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId()).size();
        Long graphRunNodes = neo4jClient.query("""
            MATCH (run)
            WHERE run:ExtractionRun OR run:DocumentProcessingRun
            RETURN count(run) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(nodeEvidence).isEqualTo(1L);
        assertThat(relationshipEvidence).isEqualTo(1L);
        assertThat(extractionRuns).isEqualTo(2);
        assertThat(processingRuns).isEqualTo(2);
        assertThat(graphRunNodes).isZero();
        Long parentScopedEvidence = neo4jClient.query("""
            MATCH (e:GraphExtractionEvidence {sourceDocumentId: $documentId})
            WHERE e.sourceChunkKind = 'PARENT'
              AND e.sourceChunkId STARTS WITH 'parent_'
              AND e.processingRunId IS NOT NULL
              AND e.effectiveChunkerRevision IS NOT NULL
            RETURN count(e) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(parentScopedEvidence).isEqualTo(3L);

        documentUploadService.replace(
            "kb-1",
            uploaded.getId(),
            new MockMultipartFile(
                "file",
                "replacement.txt",
                "text/plain",
                "replacement contract content".getBytes()
            )
        );
        assertThat(extractionRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId())).isEmpty();
        assertThat(processingRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId())).isEmpty();
        DocumentUploadNode replacementProcessed = documentProcessingService.process(uploaded.getId());
        assertThat(replacementProcessed.getStatus().name()).isEqualTo("COMPLETED");

        String planId = reprocessingService.create("kb-1", new CreatePlanRequest(
            null, null, false, List.of(uploaded.getId()), Map.of(),
            ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION, ChunkReprocessingSelection.DOCUMENT_IDS,
            chunkingService.migrationTargetRevision())).planId();
        SchemaReprocessingPlanNode migration = planRepository.findById(planId).orElseThrow();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while ((migration.getStatus() == SchemaReprocessingPlanStatus.QUEUED
            || migration.getStatus() == SchemaReprocessingPlanStatus.RUNNING) && System.nanoTime() < deadline) {
            Thread.sleep(25);
            migration = planRepository.findById(planId).orElseThrow();
        }
        assertThat(migration.getStatus()).isEqualTo(SchemaReprocessingPlanStatus.COMPLETED);
        assertThat(migration.getSucceededDocuments()).isEqualTo(1);
        List<DocumentProcessingRunNode> migratedHistory =
            processingRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId());
        assertThat(migratedHistory).hasSize(2);
        assertThat(migratedHistory).filteredOn(DocumentProcessingRunNode::isActiveCompleted)
            .singleElement().satisfies(run -> {
                assertThat(run.getStatus()).isEqualTo(DocumentProcessingRunStatus.COMPLETED);
                assertThat(run.getSourceSha256()).isEqualTo(replacementProcessed.getSha256());
            });
    }

    @Test
    void processesStructuredPdfAndDocxThroughFlatSectionConsumers() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        String schemaJson = """
            {
              "name": "structured-documents",
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

        DocumentUploadNode pdf = documentUploadService.upload(
            "kb-1",
            new MockMultipartFile(
                "file",
                "structured.pdf",
                "application/pdf",
                pdfPageWithLines("PDF contract paragraph", "PDF second line")
            )
        );
        DocumentUploadNode docx = documentUploadService.upload(
            "kb-1",
            new MockMultipartFile(
                "file",
                "structured.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes()
            )
        );

        assertThat(documentProcessingService.process(
            pdf.getId(),
            false,
            java.util.Map.of("pdf.split-pages", true)
        ).getStatus().name()).isEqualTo("COMPLETED");
        assertThat(documentProcessingService.process(docx.getId()).getStatus().name()).isEqualTo("COMPLETED");

        assertThat(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(pdf.getId()))
            .extracting(DocumentChunkNode::getText)
            .anySatisfy(text -> assertThat(text)
                .contains("PDF contract paragraph")
                .contains("PDF second line"));
        assertThat(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(pdf.getId()).stream()
            .filter(chunk -> "CHILD".equals(chunk.getKind())).toList())
            .allSatisfy(chunk -> {
                assertThat(chunk.getPageStart()).isNotNull();
                assertThat(chunk.getPageEnd()).isEqualTo(chunk.getPageStart());
                assertThat(chunk.getKind()).isEqualTo("CHILD");
            });
        assertThat(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(docx.getId()))
            .extracting(DocumentChunkNode::getText)
            .anySatisfy(text -> assertThat(text)
                .contains("Heading 1")
                .contains("Cell A")
                .contains("Custom style text"));
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
