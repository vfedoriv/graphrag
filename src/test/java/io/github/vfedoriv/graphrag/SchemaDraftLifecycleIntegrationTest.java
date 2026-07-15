package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.discovery.CandidateExtractionModelAdapter;
import io.github.vfedoriv.graphrag.discovery.CandidateExtractionResult;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftDecisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.Neo4jPersistenceVersionBackfillService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SchemaDraftLifecycleIntegrationTest.DraftAiConfig.class})
@TestPropertySource(properties = {
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
    "app.storage.documents-root=./target/test-documents",
    "app.schema-discovery.chunk-characters=1000",
    "app.schema-draft.analysis.concurrency=1",
    "app.schema-draft.analysis.queue-capacity=2"
})
class SchemaDraftLifecycleIntegrationTest {
    private static final String KNOWLEDGE_BASE_ID = "kb-draft";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicBoolean BLOCK_MODEL = new AtomicBoolean();
    private static final AtomicBoolean FAIL_REPROCESSING = new AtomicBoolean();
    private static volatile CountDownLatch MODEL_ENTERED = new CountDownLatch(1);
    private static volatile CountDownLatch RELEASE_MODEL = new CountDownLatch(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private Neo4jClient neo4jClient;
    @Autowired private SchemaRegistryService schemaRegistryService;
    @Autowired private DocumentUploadService documentUploadService;
    @Autowired private SchemaDraftRepository draftRepository;
    @Autowired private SchemaDraftSourceRepository sourceRepository;
    @Autowired private SchemaDraftAnalysisRunRepository runRepository;
    @Autowired private SchemaDraftAggregateRevisionRepository aggregateRepository;
    @Autowired private SchemaDraftDecisionRepository decisionRepository;
    @Autowired private Neo4jPersistenceVersionBackfillService versionBackfillService;

    @BeforeEach
    void setUp() throws Exception {
        resetModelGate();
        FAIL_REPROCESSING.set(false);
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        TestDocumentStorage.clean();
        mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType("application/json")
                .content("{\"id\":\"" + KNOWLEDGE_BASE_ID + "\",\"name\":\"Draft KB\"}"))
            .andExpect(status().isOk());
    }

    @AfterEach
    void cleanStorage() throws Exception {
        RELEASE_MODEL.countDown();
        BLOCK_MODEL.set(false);
        FAIL_REPROCESSING.set(false);
        TestDocumentStorage.clean();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void persistsFullCreateAddAnalyzeReviewReanalyzeDiffDeleteLifecycle(CapturedOutput output) throws Exception {
        SchemaDefinitionNode base = schemaRegistryService.createSchema("""
            {"name":"people","version":1,"nodes":[{"label":"Person","key":["personId"],
            "properties":[{"name":"personId","type":"STRING","required":true}]}],"relationships":[]}
            """, SchemaSourceType.PREDEFINED, KNOWLEDGE_BASE_ID);

        JsonNode draft = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            """
                {"targetName":"people","targetVersion":2,"baseSchemaId":"%s",
                "guidance":{"guidance":{"requiredConcepts":[{"name":"Person","identityKeys":["personId"]}]}}}
                """.formatted(base.getId()), KNOWLEDGE_BASE_ID));
        String draftId = draft.path("id").asText();
        assertThat(draft.path("revision").asLong()).isZero();

        JsonNode source = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
            """
                {"revision":0,"name":"sample","text":"private-draft-source Person personId Alice"}
                """, KNOWLEDGE_BASE_ID, draftId));
        assertThat(source.path("type").asText()).isEqualTo("TEXT");
        assertThat(source.has("contentUri")).isFalse();
        assertThat(source.toString()).doesNotContain("private-draft-source");

        JsonNode accepted = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftId));
        String firstRunId = accepted.path("runId").asText();
        JsonNode completed = awaitTerminal(draftId, firstRunId);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("currentResult").asBoolean()).isTrue();
        assertThat(completed.path("sourceOutcomes").path("content").get(0).path("reused").asBoolean()).isFalse();
        assertThat(completed.path("sourceOutcomes").path("page").asInt()).isZero();
        assertThat(completed.path("sourceOutcomes").path("totalElements").asLong()).isEqualTo(1);
        JsonNode emptyAnalysisPage = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs/{runId}",
                KNOWLEDGE_BASE_ID, draftId, firstRunId).param("page", "3").param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        assertThat(emptyAnalysisPage.path("sourceOutcomes").path("content")).isEmpty();
        assertThat(emptyAnalysisPage.path("sourceOutcomes").path("totalElements").asLong()).isEqualTo(1);
        assertThat(emptyAnalysisPage.path("succeededSources").asInt()).isEqualTo(1);

        JsonNode retryAccepted = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs/{runId}/retry",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftId, firstRunId));
        JsonNode retryCompleted = awaitTerminal(draftId, retryAccepted.path("runId").asText());
        assertThat(retryCompleted.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(retryCompleted.path("sourceOutcomes").path("content").get(0).path("reused").asBoolean()).isTrue();
        JsonNode analysisHistory = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
                KNOWLEDGE_BASE_ID, draftId).param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        assertThat(analysisHistory.path("totalElements").asLong()).isEqualTo(2);
        assertThat(analysisHistory.path("content").get(0).path("id").asText())
            .isEqualTo(retryAccepted.path("runId").asText());
        assertThat(analysisHistory.path("content").get(0).path("retryOfRunId").asText()).isEqualTo(firstRunId);
        assertThat(analysisHistory.path("content").get(0).path("current").asBoolean()).isTrue();
        JsonNode draftWithAnalysis = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}", KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(draftWithAnalysis.path("currentAnalysis").path("id").asText())
            .isEqualTo(retryAccepted.path("runId").asText());

        JsonNode candidates = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/candidates",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(candidates.path("content").toString()).contains("EXISTING", "OBSERVED");
        String candidateIdentity = "node-property:Person:displayName";

        JsonNode decision = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/decisions",
            """
                {"revision":1,"type":"REJECT","candidateIdentity":"%s","rationale":"reviewed"}
                """.formatted(candidateIdentity), KNOWLEDGE_BASE_ID, draftId));
        assertThat(decision.path("reviewState").asText()).isEqualTo("REJECTED");
        JsonNode reviewedCandidates = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/candidates",
                KNOWLEDGE_BASE_ID, draftId).param("size", "100"))
            .andExpect(status().isOk()).andReturn());
        JsonNode rejected = java.util.stream.StreamSupport.stream(
                reviewedCandidates.path("content").spliterator(), false)
            .filter(value -> candidateIdentity.equals(value.path("identity").asText())).findFirst().orElseThrow();
        assertThat(rejected.path("effectiveReviewState").asText()).isEqualTo("REJECTED");
        assertThat(rejected.path("latestDecisionId").asText()).isEqualTo(decision.path("id").asText());
        assertThat(rejected.path("evidence").isArray()).isTrue();

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/projection",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schema.name").value("people"));
        MvcResult firstDiff = mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/diff",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.changes").isArray())
            .andReturn();
        MvcResult secondDiff = mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/diff",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(secondDiff.getResponse().getContentAsString())
            .isEqualTo(firstDiff.getResponse().getContentAsString());

        JsonNode updated = json(mockMvc.perform(put(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/guidance",
                KNOWLEDGE_BASE_ID, draftId)
                .contentType("application/json")
                .content("""
                    {"revision":2,"guidance":{"additionalInstructions":"private-guidance-value"}}
                    """))
            .andExpect(status().isOk()).andReturn());
        assertThat(updated.path("revision").asLong()).isEqualTo(3);
        assertThat(updated.path("currentAggregateId").isNull()).isTrue();

        JsonNode secondAccepted = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":3}", KNOWLEDGE_BASE_ID, draftId));
        JsonNode secondCompleted = awaitTerminal(draftId, secondAccepted.path("runId").asText());
        assertThat(secondCompleted.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(secondCompleted.path("sourceOutcomes").path("content").get(0).path("reused").asBoolean()).isFalse();
        assertThat(decisionRepository.findByDraftIdOrderBySequenceAsc(draftId)).hasSize(1);

        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}",
                KNOWLEDGE_BASE_ID, draftId).param("revision", "3"))
            .andExpect(status().isNoContent());
        assertThat(draftRepository.findById(draftId)).isEmpty();
        assertThat(sourceRepository.findByDraftIdOrderByCreatedAtAsc(draftId)).isEmpty();
        assertThat(runRepository.findByDraftIdOrderByCreatedAtDesc(draftId)).isEmpty();
        assertThat(aggregateRepository.findByDraftIdOrderByRevisionDesc(draftId)).isEmpty();
        assertThat(output.getAll()).doesNotContain("private-draft-source", "private-guidance-value",
            "node-property:Person:displayName", "displayName", "node:Person");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void evaluatesPublishesActivatesAndRetriesAPartialReprocessingPlan(CapturedOutput output) throws Exception {
        JsonNode draft = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            "{\"targetName\":\"evaluated-people\",\"targetVersion\":1,\"guidance\":{}}", KNOWLEDGE_BASE_ID));
        String draftId = draft.path("id").asText();
        postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
            "{\"revision\":0,\"name\":\"evidence\",\"text\":\"Person personId discovery evidence\"}",
            KNOWLEDGE_BASE_ID, draftId);
        JsonNode analysis = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftId));
        assertThat(awaitTerminal(draftId, analysis.path("runId").asText()).path("status").asText())
            .isEqualTo("COMPLETED");

        String heldOutText = "held-out-private-content Person P-100";
        DocumentUploadNode heldOut = documentUploadService.upload(KNOWLEDGE_BASE_ID,
            new MockMultipartFile("file", "held-out.txt", "text/plain", heldOutText.getBytes()));
        JsonNode eligibility = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-eligible-documents",
                KNOWLEDGE_BASE_ID, draftId).param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        assertThat(eligibility.path("draftRevision").asLong()).isEqualTo(1);
        assertThat(eligibility.path("currentAggregateId").asText()).isNotBlank();
        assertThat(eligibility.path("content").get(0).path("documentId").asText()).isEqualTo(heldOut.getId());
        assertThat(eligibility.path("content").get(0).path("eligible").asBoolean()).isTrue();
        JsonNode evaluation = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs",
            "{\"revision\":1,\"documentIds\":[\"%s\"],\"advisoryEnabled\":true}"
                .formatted(heldOut.getId()), KNOWLEDGE_BASE_ID, draftId));
        JsonNode evaluated = awaitEvaluationTerminal(draftId, evaluation.path("runId").asText());
        assertThat(evaluated.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(evaluated.path("succeededDocuments").asInt()).isEqualTo(1);
        assertThat(evaluated.path("advisoryAssessment").path("status").asText())
            .isEqualTo("COMPLETED_WITHOUT_MODEL_JUDGMENT");
        assertThat(evaluated.path("contractRevision").asText()).isEqualTo("schema-draft-evaluation-v2");
        assertThat(evaluated.path("outcomes").path("totalElements").asLong()).isEqualTo(1);
        assertThat(evaluated.path("metrics").path("rates").isArray()).isTrue();
        assertThat(evaluated.path("advisoryAssessment").path("reproducibility")
            .path("contractRevision").asText()).isEqualTo("schema-draft-evaluation-v2");
        assertThat(countNodes("DocumentChunk") + countNodes("ExtractionRun")).isZero();
        JsonNode evaluationHistory = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        String rediscoveredEvaluationId = evaluationHistory.path("content").get(0).path("id").asText();
        assertThat(rediscoveredEvaluationId).isEqualTo(evaluation.path("runId").asText());
        assertThat(evaluationHistory.path("content").get(0).path("current").asBoolean()).isTrue();
        assertThat(json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs/{runId}",
                KNOWLEDGE_BASE_ID, draftId, rediscoveredEvaluationId))
            .andExpect(status().isOk()).andReturn()).path("status").asText()).isEqualTo("COMPLETED");

        JsonNode readiness = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/publication-readiness",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(readiness.path("ready").asBoolean()).isTrue();
        String projectionHash = readiness.path("projectionContentHash").asText();
        JsonNode publication = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/publish",
            "{\"revision\":1,\"projectionContentHash\":\"%s\"}".formatted(projectionHash),
            KNOWLEDGE_BASE_ID, draftId));
        String schemaId = publication.path("schemaId").asText();
        assertThat(schemaRegistryService.getSchema(schemaId).getStatus().name()).isEqualTo("INACTIVE");
        assertThat(countNodes("DocumentChunk") + countNodes("ExtractionRun")).isZero();
        JsonNode repeated = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/publish",
            "{\"revision\":1,\"projectionContentHash\":\"%s\"}".formatted(projectionHash),
            KNOWLEDGE_BASE_ID, draftId));
        assertThat(repeated.path("publicationId").asText()).isEqualTo(publication.path("publicationId").asText());

        SchemaDefinitionNode published = schemaRegistryService.getSchema(schemaId);
        String editedContent = published.getContent().replace("\"relationships\":[]",
            "\"relationships\":[],\"description\":\"editable while inactive\"");
        schemaRegistryService.updateSchema(schemaId, editedContent, SchemaSourceType.GENERATED);
        JsonNode drifted = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/publication",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(drifted.path("contentDrifted").asBoolean()).isTrue();

        schemaRegistryService.activateSchema(KNOWLEDGE_BASE_ID, schemaId);
        assertThatThrownBy(() -> schemaRegistryService.updateSchema(schemaId, editedContent, SchemaSourceType.GENERATED))
            .isInstanceOf(ConflictException.class);

        DocumentUploadNode failing = documentUploadService.upload(KNOWLEDGE_BASE_ID,
            new MockMultipartFile("file", "failing.txt", "text/plain", "FAIL_REPROCESSING".getBytes()));
        FAIL_REPROCESSING.set(true);
        JsonNode plan = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans",
            "{\"draftId\":\"%s\",\"schemaId\":\"%s\",\"allDocuments\":true,\"processingOptions\":{}}"
                .formatted(draftId, schemaId), KNOWLEDGE_BASE_ID));
        JsonNode partial = awaitPlanTerminal(plan.path("planId").asText());
        assertThat(partial.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(partial.path("succeededDocuments").asInt()).isEqualTo(1);
        assertThat(partial.path("failedDocuments").asInt()).isEqualTo(1);
        JsonNode firstPlanPage = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans/{planId}",
                KNOWLEDGE_BASE_ID, plan.path("planId").asText()).param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        JsonNode laterPlanPage = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans/{planId}",
                KNOWLEDGE_BASE_ID, plan.path("planId").asText()).param("page", "1").param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        JsonNode emptyPlanPage = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans/{planId}",
                KNOWLEDGE_BASE_ID, plan.path("planId").asText()).param("page", "9").param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        assertThat(firstPlanPage.path("items").path("content")).hasSize(1);
        assertThat(laterPlanPage.path("items").path("content")).hasSize(1);
        assertThat(emptyPlanPage.path("items").path("content")).isEmpty();
        assertThat(emptyPlanPage.path("items").path("totalElements").asLong()).isEqualTo(2);
        assertThat(emptyPlanPage.path("succeededDocuments").asInt()).isEqualTo(1);
        assertThat(emptyPlanPage.path("failedDocuments").asInt()).isEqualTo(1);

        FAIL_REPROCESSING.set(false);
        JsonNode retry = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans/{planId}/retry",
            "{\"resnapshotUnresolvedDocuments\":true}", KNOWLEDGE_BASE_ID, plan.path("planId").asText()));
        JsonNode completed = awaitPlanTerminal(retry.path("planId").asText());
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("totalDocuments").asInt()).isEqualTo(1);
        assertThat(completed.path("items").path("content").get(0).path("documentId").asText()).isEqualTo(failing.getId());
        JsonNode planHistory = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans", KNOWLEDGE_BASE_ID)
                .param("draftId", draftId).param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        String rediscoveredPlanId = planHistory.path("content").get(0).path("id").asText();
        assertThat(rediscoveredPlanId).isEqualTo(retry.path("planId").asText());
        assertThat(planHistory.path("content").get(0).path("retryOfPlanId").asText())
            .isEqualTo(plan.path("planId").asText());
        assertThat(planHistory.path("content").get(0).path("latest").asBoolean()).isTrue();
        assertThat(planHistory.path("content").get(0).path("targetCurrent").asBoolean()).isTrue();
        JsonNode draftNavigation = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}", KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(draftNavigation.path("latestEvaluation").path("id").asText())
            .isEqualTo(evaluation.path("runId").asText());
        assertThat(draftNavigation.path("latestReprocessing").path("id").asText()).isEqualTo(rediscoveredPlanId);
        SchemaDefinitionNode replacement = schemaRegistryService.createSchema("""
            {"name":"replacement","version":1,"nodes":[{"label":"Person","key":["personId"],
            "properties":[{"name":"personId","type":"STRING","required":true}]}],"relationships":[]}
            """, SchemaSourceType.GENERATED, KNOWLEDGE_BASE_ID);
        schemaRegistryService.activateSchema(KNOWLEDGE_BASE_ID, replacement.getId());
        JsonNode staleTargetHistory = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans", KNOWLEDGE_BASE_ID)
                .param("draftId", draftId).param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        assertThat(staleTargetHistory.path("content").get(0).path("targetCurrent").asBoolean()).isFalse();
        assertThat(staleTargetHistory.path("content").get(0).path("retryable").asBoolean()).isFalse();
        assertThat(output.getAll()).doesNotContain(heldOutText, "Person P-100", "node:Person",
            "schema-draft-evaluation-v2\"", "POSSIBLE_NOISE");
    }

    @Test
    void discoversAuthoritativeEligibilityAndStaleEvaluationHistory() throws Exception {
        JsonNode draft = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            "{\"targetName\":\"navigation\",\"targetVersion\":1,\"guidance\":{}}", KNOWLEDGE_BASE_ID));
        String draftId = draft.path("id").asText();
        DocumentUploadNode evidence = documentUploadService.upload(KNOWLEDGE_BASE_ID,
            new MockMultipartFile("file", "evidence.txt", "text/plain", "Person E-1 evidence".getBytes()));
        DocumentUploadNode heldOut = documentUploadService.upload(KNOWLEDGE_BASE_ID,
            new MockMultipartFile("file", "held-out.txt", "text/plain", "Person H-1 held out".getBytes()));
        JsonNode source = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/documents",
            "{\"revision\":0,\"documentId\":\"%s\"}".formatted(evidence.getId()), KNOWLEDGE_BASE_ID, draftId));
        JsonNode started = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftId));
        awaitTerminal(draftId, started.path("runId").asText());

        JsonNode eligibility = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-eligible-documents",
                KNOWLEDGE_BASE_ID, draftId).param("size", "100"))
            .andExpect(status().isOk()).andReturn());
        JsonNode evidenceEligibility = findDocument(eligibility.path("content"), evidence.getId());
        JsonNode heldOutEligibility = findDocument(eligibility.path("content"), heldOut.getId());
        assertThat(evidenceEligibility.path("eligible").asBoolean()).isFalse();
        assertThat(evidenceEligibility.path("ineligibilityReason").asText())
            .isEqualTo("ACTIVE_DISCOVERY_EVIDENCE");
        assertThat(heldOutEligibility.path("eligible").asBoolean()).isTrue();
        assertThat(heldOutEligibility.path("ineligibilityReason").isNull()).isTrue();

        assertEligibilityAfterMutation(draftId, source.path("id").asText(),
            "MATCH (result:SchemaDraftSourceResult {sourceId: $sourceId}) SET result.status = 'FAILED'",
            evidence.getId(), true);
        assertEligibilityAfterMutation(draftId, source.path("id").asText(),
            "MATCH (result:SchemaDraftSourceResult {sourceId: $sourceId}) SET result.status = 'SUCCEEDED'",
            evidence.getId(), false);
        assertEligibilityAfterMutation(draftId, source.path("id").asText(),
            "MATCH (source:SchemaDraftSource {id: $sourceId}) SET source.revision = 1",
            evidence.getId(), true);
        assertEligibilityAfterMutation(draftId, source.path("id").asText(),
            "MATCH (source:SchemaDraftSource {id: $sourceId}) SET source.revision = 0, source.status = 'REMOVED'",
            evidence.getId(), true);
        assertEligibilityAfterMutation(draftId, source.path("id").asText(),
            "MATCH (source:SchemaDraftSource {id: $sourceId}) SET source.status = 'ACTIVE', source.type = 'TEXT'",
            evidence.getId(), true);
        assertEligibilityAfterMutation(draftId, source.path("id").asText(),
            "MATCH (source:SchemaDraftSource {id: $sourceId}) SET source.type = 'DOCUMENT'",
            evidence.getId(), false);

        JsonNode firstEligibilityPage = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-eligible-documents",
                KNOWLEDGE_BASE_ID, draftId).param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        JsonNode secondEligibilityPage = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-eligible-documents",
                KNOWLEDGE_BASE_ID, draftId).param("page", "1").param("size", "1"))
            .andExpect(status().isOk()).andReturn());
        assertThat(firstEligibilityPage.path("totalElements").asLong()).isEqualTo(2);
        assertThat(firstEligibilityPage.path("content")).hasSize(1);
        assertThat(secondEligibilityPage.path("content")).hasSize(1);

        mockMvc.perform(post(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs",
                KNOWLEDGE_BASE_ID, draftId).contentType("application/json")
                .content("{\"revision\":1,\"documentIds\":[\"%s\"],\"advisoryEnabled\":false}"
                    .formatted(evidence.getId())))
            .andExpect(status().isBadRequest());
        JsonNode evaluation = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs",
            "{\"revision\":1,\"documentIds\":[\"%s\"],\"advisoryEnabled\":false}"
                .formatted(heldOut.getId()), KNOWLEDGE_BASE_ID, draftId));
        awaitEvaluationTerminal(draftId, evaluation.path("runId").asText());

        mockMvc.perform(put(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/guidance",
                KNOWLEDGE_BASE_ID, draftId).contentType("application/json")
                .content("{\"revision\":1,\"guidance\":{\"additionalInstructions\":\"changed\"}}"))
            .andExpect(status().isOk());
        JsonNode history = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(history.path("content").get(0).path("current").asBoolean()).isFalse();
        assertThat(history.path("content").get(0).path("retryable").asBoolean()).isFalse();
        JsonNode response = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}", KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(response.path("currentAnalysis").isNull()).isTrue();
        assertThat(response.path("latestEvaluation").path("current").asBoolean()).isFalse();
        mockMvc.perform(post(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs",
                KNOWLEDGE_BASE_ID, draftId).contentType("application/json")
                .content("{\"revision\":1,\"documentIds\":[\"%s\"],\"advisoryEnabled\":false}"
                    .formatted(heldOut.getId())))
            .andExpect(status().isConflict());

        mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
                KNOWLEDGE_BASE_ID, "foreign-draft"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs",
                KNOWLEDGE_BASE_ID, "foreign-draft"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-eligible-documents",
                KNOWLEDGE_BASE_ID, "foreign-draft"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans", KNOWLEDGE_BASE_ID)
                .param("draftId", "foreign-draft"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsQueueOverloadAndDoesNotPromoteAStaleSnapshot() throws Exception {
        BLOCK_MODEL.set(true);
        List<String> draftIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            draftIds.add(createDraftWithText("queue-" + index));
        }

        JsonNode first = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftIds.get(0)));
        assertThat(MODEL_ENTERED.await(5, TimeUnit.SECONDS)).isTrue();
        JsonNode matching = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftIds.get(0)));
        assertThat(matching.path("runId").asText()).isEqualTo(first.path("runId").asText());
        JsonNode runningHistory = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
                KNOWLEDGE_BASE_ID, draftIds.get(0)))
            .andExpect(status().isOk()).andReturn());
        assertThat(runningHistory.path("content").get(0).path("current").asBoolean()).isTrue();
        assertThat(runningHistory.path("content").get(0).path("retryable").asBoolean()).isFalse();

        JsonNode second = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftIds.get(1)));
        JsonNode third = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftIds.get(2)));
        mockMvc.perform(post(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
                KNOWLEDGE_BASE_ID, draftIds.get(3)).contentType("application/json").content("{\"revision\":1}"))
            .andExpect(status().isConflict());

        mockMvc.perform(put(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/guidance",
                KNOWLEDGE_BASE_ID, draftIds.get(0)).contentType("application/json")
                .content("{\"revision\":1,\"guidance\":{\"guidance\":{\"domainDescription\":\"changed\"}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(2));

        RELEASE_MODEL.countDown();
        JsonNode stale = awaitTerminal(draftIds.get(0), first.path("runId").asText());
        assertThat(stale.path("currentResult").asBoolean()).isFalse();
        awaitTerminal(draftIds.get(1), second.path("runId").asText());
        awaitTerminal(draftIds.get(2), third.path("runId").asText());
    }

    @Test
    void atomicallyReservesOneRunAcrossConcurrentClaimers() throws Exception {
        JsonNode draft = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            "{\"targetName\":\"claims\",\"targetVersion\":1,\"guidance\":{}}", KNOWLEDGE_BASE_ID));
        String draftId = draft.path("id").asText();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Long>> claims = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                String runId = "run-" + index;
                claims.add(executor.submit(() -> {
                    try {
                        return draftRepository.reserveAnalysis(draftId, runId);
                    } catch (RuntimeException exception) {
                        return 0L;
                    }
                }));
            }
            long winners = 0;
            for (Future<Long> claim : claims) {
                winners += claim.get(5, TimeUnit.SECONDS);
            }
            assertThat(winners).isEqualTo(1);
        }
    }

    @Test
    void backfillsPersistenceVersionsForEveryDraftEntityType() {
        neo4jClient.query("""
            CREATE (:SchemaDraft {id: 'legacy-draft'}),
                   (:SchemaDraftSource {id: 'legacy-source'}),
                   (:SchemaDraftSourceRevision {id: 'legacy-source-revision'}),
                   (:SchemaDraftAnalysisRun {id: 'legacy-run'}),
                   (:SchemaDraftSourceResult {id: 'legacy-result'}),
                   (:SchemaDraftAggregateRevision {id: 'legacy-aggregate'}),
                   (:SchemaDraftDecision {id: 'legacy-decision'}),
                   (:SchemaDraftConflict {id: 'legacy-conflict'}),
                   (:SchemaDraftStorageMutation {id: 'legacy-mutation'})
            """).run();

        versionBackfillService.run(null);

        Long count = neo4jClient.query("""
            MATCH (n)
            WHERE (n:SchemaDraft OR n:SchemaDraftSource OR n:SchemaDraftSourceRevision OR
                   n:SchemaDraftAnalysisRun OR n:SchemaDraftSourceResult OR
                   n:SchemaDraftAggregateRevision OR n:SchemaDraftDecision OR
                   n:SchemaDraftConflict OR n:SchemaDraftStorageMutation)
              AND n.id STARTS WITH 'legacy-'
              AND n.persistenceVersion = 0
            RETURN count(n)
            """).fetchAs(Long.class).one().orElse(0L);
        assertThat(count).isEqualTo(9L);
    }

    @Test
    void rejectsStaleRevisionAndDuplicateTextReturnsExistingSource() throws Exception {
        JsonNode draft = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            "{\"targetName\":\"new-schema\",\"targetVersion\":1,\"guidance\":{}}", KNOWLEDGE_BASE_ID));
        String draftId = draft.path("id").asText();
        String payload = "{\"revision\":0,\"name\":\"sample\",\"text\":\"same content\"}";
        JsonNode first = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
            payload, KNOWLEDGE_BASE_ID, draftId));

        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
                KNOWLEDGE_BASE_ID, draftId).contentType("application/json").content(payload))
            .andExpect(status().isConflict());

        String currentPayload = "{\"revision\":1,\"name\":\"duplicate\",\"text\":\"same content\"}";
        JsonNode duplicate = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
            currentPayload, KNOWLEDGE_BASE_ID, draftId));
        assertThat(duplicate.path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(draftRepository.findById(draftId).orElseThrow().getRevision()).isEqualTo(1);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void retainsPreparedChunkCountsAndLogsOnlySafeFailureMetadata(CapturedOutput output) throws Exception {
        JsonNode draft = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            "{\"targetName\":\"failure-progress\",\"targetVersion\":1,\"guidance\":{}}", KNOWLEDGE_BASE_ID));
        String draftId = draft.path("id").asText();
        JsonNode preparedFailure = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
            "{\"revision\":0,\"name\":\"prepared\",\"text\":\"FAIL_CANDIDATE_PRIVATE_SOURCE\"}",
            KNOWLEDGE_BASE_ID, draftId));
        JsonNode preparationFailure = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
            "{\"revision\":1,\"name\":\"unavailable\",\"text\":\"PRIVATE_PREPARATION_SOURCE\"}",
            KNOWLEDGE_BASE_ID, draftId));
        neo4jClient.query("MATCH (source:SchemaDraftSource {id: $sourceId}) SET source.contentUri = $contentUri")
            .bind(preparationFailure.path("id").asText()).to("sourceId")
            .bind("file:///private-missing-source.txt").to("contentUri").run();

        JsonNode accepted = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs",
            "{\"revision\":2}", KNOWLEDGE_BASE_ID, draftId));
        JsonNode failed = awaitTerminal(draftId, accepted.path("runId").asText());

        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        JsonNode outcomes = failed.path("sourceOutcomes").path("content");
        JsonNode preparedOutcome = java.util.stream.StreamSupport.stream(outcomes.spliterator(), false)
            .filter(value -> preparedFailure.path("id").asText().equals(value.path("sourceId").asText()))
            .findFirst().orElseThrow();
        JsonNode preparationOutcome = java.util.stream.StreamSupport.stream(outcomes.spliterator(), false)
            .filter(value -> preparationFailure.path("id").asText().equals(value.path("sourceId").asText()))
            .findFirst().orElseThrow();
        assertThat(preparedOutcome.path("chunkCount").asInt()).isEqualTo(1);
        assertThat(preparationOutcome.path("chunkCount").asInt()).isZero();
        assertThat(output.getAll()).contains(
            "preparedChunkCount=1", "preparedChunkCount=0", "exceptionType=IllegalArgumentException",
            "messageFingerprint=sha256:");
        assertThat(output.getAll()).doesNotContain(
            "FAIL_CANDIDATE_PRIVATE_SOURCE", "PRIVATE_PREPARATION_SOURCE",
            "PRIVATE_CANDIDATE_RESPONSE");
    }

    @Test
    void readsLegacyGuidanceShapesAndCanonicalizesThemOnUpdate() throws Exception {
        List<String> legacyValues = List.of(
            "{\"domainDescription\":\"direct\",\"intendedQuestions\":[\"who\"]}",
            "{\"additionalInstructions\":\"instructions only\"}",
            "{\"additionalInstructions\":\"wrapped instructions\",\"guidance\":{\"domainDescription\":\"wrapped\"}}"
        );
        for (int index = 0; index < legacyValues.size(); index++) {
            JsonNode created = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
                "{\"targetName\":\"legacy-" + index + "\",\"targetVersion\":1,\"guidance\":{}}",
                KNOWLEDGE_BASE_ID));
            String draftId = created.path("id").asText();
            String legacy = legacyValues.get(index);
            neo4jClient.query("MATCH (d:SchemaDraft {id: $id}) SET d.guidanceJson = $guidance")
                .bind(draftId).to("id").bind(legacy).to("guidance").run();

            JsonNode read = json(mockMvc.perform(get(
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}", KNOWLEDGE_BASE_ID, draftId))
                .andExpect(status().isOk()).andReturn());
            assertThat(read.path("guidance").path("guidance").path("intendedQuestions").isArray()).isTrue();

            JsonNode updated = json(mockMvc.perform(put(
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/guidance",
                    KNOWLEDGE_BASE_ID, draftId).contentType("application/json")
                    .content("{\"revision\":0,\"guidance\":{\"additionalInstructions\":\"canonical\"}}"))
                .andExpect(status().isOk()).andReturn());
            assertThat(updated.path("guidance").path("additionalInstructions").asText()).isEqualTo("canonical");
            assertThat(draftRepository.findById(draftId).orElseThrow().getGuidanceJson())
                .contains("\"additionalInstructions\":\"canonical\"", "\"guidance\"");
        }
    }

    @Test
    void invalidGuidanceDoesNotMutateDraftState() throws Exception {
        JsonNode created = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            "{\"targetName\":\"strict-guidance\",\"targetVersion\":1,\"guidance\":{\"additionalInstructions\":\"saved\"}}",
            KNOWLEDGE_BASE_ID));
        String draftId = created.path("id").asText();
        String savedGuidance = draftRepository.findById(draftId).orElseThrow().getGuidanceJson();

        mockMvc.perform(put("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/guidance",
                KNOWLEDGE_BASE_ID, draftId).contentType("application/json")
                .content("{\"revision\":0,\"guidance\":{\"unknown\":true}}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/guidance",
                KNOWLEDGE_BASE_ID, draftId).contentType("application/json")
                .content("{\"revision\":0,\"guidance\":{\"guidance\":{\"propertyRules\":[{\"owner\":\"Person\",\"name\":\"id\",\"type\":\"INVALID\"}]}}}"))
            .andExpect(status().isBadRequest());

        SchemaDraftNode unchanged = draftRepository.findById(draftId).orElseThrow();
        assertThat(unchanged.getRevision()).isZero();
        assertThat(unchanged.getGuidanceRevision()).isZero();
        assertThat(unchanged.getGuidanceJson()).isEqualTo(savedGuidance);
    }

    private MvcResult postJson(String path, String content, Object... variables) throws Exception {
        return mockMvc.perform(post(path, variables).contentType("application/json").content(content))
            .andExpect(status().is2xxSuccessful()).andReturn();
    }

    private JsonNode awaitTerminal(String draftId, String runId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        JsonNode response;
        do {
            response = json(mockMvc.perform(get(
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs/{runId}",
                    KNOWLEDGE_BASE_ID, draftId, runId))
                .andExpect(status().isOk()).andReturn());
            if (!"RUNNING".equals(response.path("status").asText())) return response;
            Thread.sleep(25);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Analysis did not complete: " + response);
    }

    private JsonNode awaitEvaluationTerminal(String draftId, String runId) throws Exception {
        return awaitAsyncStatus(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-runs/{runId}",
            KNOWLEDGE_BASE_ID, draftId, runId);
    }

    private JsonNode awaitPlanTerminal(String planId) throws Exception {
        return awaitAsyncStatus("/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans/{planId}",
            KNOWLEDGE_BASE_ID, planId);
    }

    private JsonNode awaitAsyncStatus(String path, Object... variables) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        JsonNode response;
        do {
            response = json(mockMvc.perform(get(path, variables)).andExpect(status().isOk()).andReturn());
            String state = response.path("status").asText();
            if (!"QUEUED".equals(state) && !"RUNNING".equals(state)) return response;
            Thread.sleep(25);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Async operation did not complete: " + response);
    }

    private long countNodes(String label) {
        return neo4jClient.query("MATCH (n:" + label + ") RETURN count(n)")
            .fetchAs(Long.class).one().orElse(0L);
    }

    private String createDraftWithText(String name) throws Exception {
        JsonNode draft = json(postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts",
            "{\"targetName\":\"" + name + "\",\"targetVersion\":1,\"guidance\":{}}", KNOWLEDGE_BASE_ID));
        String draftId = draft.path("id").asText();
        postJson("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/sources/text",
            "{\"revision\":0,\"name\":\"sample\",\"text\":\"Person " + name + "\"}",
            KNOWLEDGE_BASE_ID, draftId);
        return draftId;
    }

    private void resetModelGate() {
        BLOCK_MODEL.set(false);
        MODEL_ENTERED = new CountDownLatch(1);
        RELEASE_MODEL = new CountDownLatch(1);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findDocument(JsonNode documents, String documentId) {
        return java.util.stream.StreamSupport.stream(documents.spliterator(), false)
            .filter(document -> documentId.equals(document.path("documentId").asText()))
            .findFirst().orElseThrow();
    }

    private void assertEligibilityAfterMutation(
        String draftId, String sourceId, String mutation, String documentId, boolean eligible
    ) throws Exception {
        neo4jClient.query(mutation).bind(sourceId).to("sourceId").run();
        JsonNode response = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/evaluation-eligible-documents",
                KNOWLEDGE_BASE_ID, draftId).param("size", "100"))
            .andExpect(status().isOk()).andReturn());
        assertThat(findDocument(response.path("content"), documentId).path("eligible").asBoolean())
            .isEqualTo(eligible);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DraftAiConfig {
        @Bean
        @Primary
        CandidateExtractionModelAdapter deterministicDraftCandidateExtractionModelAdapter() {
            return new CandidateExtractionModelAdapter(null, null) {
                @Override
                public CandidateExtractionResult extract(String portablePrompt) {
                    if (portablePrompt.contains("FAIL_CANDIDATE_PRIVATE_SOURCE")) {
                        throw new IllegalArgumentException("Candidate conversion failed: PRIVATE_CANDIDATE_RESPONSE");
                    }
                    if (BLOCK_MODEL.get()) {
                        MODEL_ENTERED.countDown();
                        try {
                            if (!RELEASE_MODEL.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting for model test gate");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Model test gate interrupted", exception);
                        }
                    }
                    return new CandidateExtractionResult(
                        List.of(new CandidateExtractionResult.NodeCandidate("Person", null, 0.95, "OBSERVED")),
                        List.of(
                            new CandidateExtractionResult.NodePropertyCandidate("Person", "personId", "STRING", true, 0.95, "OBSERVED"),
                            new CandidateExtractionResult.NodePropertyCandidate("Person", "displayName", "STRING", false, 0.9, "OBSERVED")
                        ),
                        List.of(new CandidateExtractionResult.NodeKeyCandidate("Person", List.of("personId"), 0.95, "OBSERVED")),
                        List.of(), List.of(), List.of()
                    );
                }
            };
        }

        @Bean
        @Primary
        EmbeddingClient deterministicEmbeddingClient() {
            return texts -> texts.stream().map(text -> List.of(0.1, 0.2, 0.3)).toList();
        }

        @Bean
        @Primary
        GraphExtractionClient deterministicGraphExtractionClient() {
            return (schema, chunkText) -> {
                if (FAIL_REPROCESSING.get() && chunkText.contains("FAIL_REPROCESSING")) {
                    throw new IllegalStateException("Deterministic extraction failure");
                }
                return new GraphExtractionResult(List.of(
                    new GraphExtractionResult.ExtractedNode("Person", Map.of("personId", "P-100"), 0.99)
                ), List.of());
            };
        }
    }
}
