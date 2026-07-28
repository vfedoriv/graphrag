package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration",
    "app.storage.documents-root=./target/test-documents"
})
class DocumentUploadIntegrationTest {

    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentUploadRepository documentUploadRepository;
    @Autowired
    private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearGraph() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        knowledgeBaseLifecycleService.provision("kb-1", "Knowledge Base 1");
        TestDocumentStorage.clean();
    }

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void persistsMetadataAndSkipsDuplicateHashWithinKnowledgeBase() {
        MockMultipartFile one = new MockMultipartFile("file", "contract.txt", "text/plain", "same-content".getBytes());
        MockMultipartFile two = new MockMultipartFile("file", "contract-copy.txt", "text/plain", "same-content".getBytes());

        DocumentUploadNode first = documentUploadService.upload("kb-1", one);
        DocumentUploadNode second = documentUploadService.upload("kb-1", two);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getSha256()).isEqualTo(second.getSha256());
        assertThat(first.getContentUri()).startsWith("file:");
        assertThat(documentUploadRepository.findAll()).hasSize(1);
    }

    @Test
    void replacementPreservesIdResetsMetadataAndClearsDerivedArtifacts() throws Exception {
        DocumentUploadNode target = documentUploadService.upload(
            "kb-1",
            new MockMultipartFile("file", "target.txt", "text/plain", "old".getBytes())
        );
        DocumentUploadNode other = documentUploadService.upload(
            "kb-1",
            new MockMultipartFile("file", "other.txt", "text/plain", "other".getBytes())
        );
        target.setStatus(DocumentStatus.COMPLETED);
        target.setProcessedAt(Instant.now());
        target.setErrorMessage("previous error");
        documentUploadRepository.save(target);
        createDerivedArtifacts(target.getId(), "run-target", "C-TARGET", "P-TARGET");
        createDerivedArtifacts(other.getId(), "run-other", "C-OTHER", "P-OTHER");
        String previousPath = documentUploadService.localPath(target);

        DocumentUploadNode replaced = documentUploadService.replace(
            "kb-1",
            target.getId(),
            new MockMultipartFile("file", "replacement.txt", "text/plain", "replacement".getBytes())
        );

        assertThat(replaced.getId()).isEqualTo(target.getId());
        assertThat(replaced.getOriginalFilename()).isEqualTo("replacement.txt");
        assertThat(replaced.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(replaced.getProcessedAt()).isNull();
        assertThat(replaced.getErrorMessage()).isNull();
        assertThat(Files.readString(Path.of(documentUploadService.localPath(replaced)))).isEqualTo("replacement");
        assertThat(Path.of(previousPath)).doesNotExist();
        assertDocumentArtifacts(target.getId(), 0L, 0L, 0L, 0L, 0L);
        assertDocumentArtifacts(other.getId(), 1L, 1L, 1L, 2L, 1L);
    }

    @Test
    void replacementRejectsDuplicateContentFromAnotherDocument() {
        DocumentUploadNode target = documentUploadService.upload(
            "kb-1",
            new MockMultipartFile("file", "target.txt", "text/plain", "target".getBytes())
        );
        documentUploadService.upload(
            "kb-1",
            new MockMultipartFile("file", "duplicate.txt", "text/plain", "duplicate".getBytes())
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> documentUploadService.replace(
                "kb-1",
                target.getId(),
                new MockMultipartFile("file", "copy.txt", "text/plain", "duplicate".getBytes())
            ))
            .isInstanceOf(ConflictException.class);

        DocumentUploadNode unchanged = documentUploadRepository.findById(target.getId()).orElseThrow();
        assertThat(unchanged.getOriginalFilename()).isEqualTo("target.txt");
    }

    @Test
    void deletionRemovesDocumentStorageAndDerivedArtifactsWithoutAffectingOtherDocuments() throws Exception {
        DocumentUploadNode target = documentUploadService.upload(
            "kb-1",
            new MockMultipartFile("file", "target.txt", "text/plain", "target".getBytes())
        );
        DocumentUploadNode other = documentUploadService.upload(
            "kb-1",
            new MockMultipartFile("file", "other.txt", "text/plain", "other".getBytes())
        );
        createDerivedArtifacts(target.getId(), "run-target", "C-TARGET", "P-TARGET");
        createDerivedArtifacts(other.getId(), "run-other", "C-OTHER", "P-OTHER");
        String targetPath = documentUploadService.localPath(target);
        String otherPath = documentUploadService.localPath(other);

        documentUploadService.delete("kb-1", target.getId());

        assertThat(documentUploadRepository.findById(target.getId())).isEmpty();
        assertThat(Path.of(targetPath)).doesNotExist();
        assertThat(Path.of(otherPath)).exists();
        assertDocumentArtifacts(target.getId(), 0L, 0L, 0L, 0L, 0L);
        assertDocumentArtifacts(other.getId(), 1L, 1L, 1L, 2L, 1L);
    }

    private void createDerivedArtifacts(String documentId, String runId, String contractId, String partyId) {
        neo4jClient.query("""
            MATCH (document:DocumentUpload {id: $documentId})
            CREATE (chunk:DocumentChunk {id: $documentId + '-chunk', documentId: $documentId, chunkIndex: 0, text: 'chunk'})
            CREATE (processingRun:DocumentProcessingRun {id: $runId + '-processing', documentId: $documentId, status: 'COMPLETED', activeCompleted: true})
            CREATE (run:ExtractionRun {id: $runId, documentId: $documentId, status: 'COMPLETED'})
            CREATE (contract:Contract {id: $contractId, contractId: $contractId, sourceDocumentId: $documentId, extractionRunId: $runId})
            CREATE (party:Party {id: $partyId, partyId: $partyId, sourceDocumentId: $documentId, extractionRunId: $runId})
            CREATE (document)-[:HAS_CHUNK]->(chunk)
            CREATE (document)-[:HAS_PROCESSING_RUN]->(processingRun)
            CREATE (document)-[:HAS_EXTRACTION_RUN]->(run)
            CREATE (run)-[:CREATED_NODE]->(contract)
            CREATE (run)-[:CREATED_NODE]->(party)
            CREATE (chunk)-[:MENTIONS]->(contract)
            CREATE (contract)-[:HAS_PARTY {sourceDocumentId: $documentId, extractionRunId: $runId}]->(party)
            """)
            .bind(documentId).to("documentId")
            .bind(runId).to("runId")
            .bind(contractId).to("contractId")
            .bind(partyId).to("partyId")
            .run();
    }

    private void assertDocumentArtifacts(
        String documentId,
        long expectedChunks,
        long expectedProcessingRuns,
        long expectedRuns,
        long expectedNodes,
        long expectedRelationships
    ) {
        Long chunks = neo4jClient.query("MATCH (c:DocumentChunk {documentId: $documentId}) RETURN count(c) AS c")
            .bind(documentId).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long runs = neo4jClient.query("MATCH (r:ExtractionRun {documentId: $documentId}) RETURN count(r) AS c")
            .bind(documentId).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long processingRuns = neo4jClient.query("MATCH (r:DocumentProcessingRun {documentId: $documentId}) RETURN count(r) AS c")
            .bind(documentId).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long nodes = neo4jClient.query("""
            MATCH (n)
            WHERE n.sourceDocumentId = $documentId
            RETURN count(n) AS c
            """)
            .bind(documentId).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long relationships = neo4jClient.query("""
            MATCH ()-[r]->()
            WHERE r.sourceDocumentId = $documentId
            RETURN count(r) AS c
            """)
            .bind(documentId).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(chunks).isEqualTo(expectedChunks);
        assertThat(processingRuns).isEqualTo(expectedProcessingRuns);
        assertThat(runs).isEqualTo(expectedRuns);
        assertThat(nodes).isEqualTo(expectedNodes);
        assertThat(relationships).isEqualTo(expectedRelationships);
    }
}
