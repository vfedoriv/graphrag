package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftDecisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import io.github.vfedoriv.graphrag.service.Neo4jPersistenceVersionBackfillService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
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
    private static volatile CountDownLatch MODEL_ENTERED = new CountDownLatch(1);
    private static volatile CountDownLatch RELEASE_MODEL = new CountDownLatch(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private Neo4jClient neo4jClient;
    @Autowired private SchemaRegistryService schemaRegistryService;
    @Autowired private SchemaDraftRepository draftRepository;
    @Autowired private SchemaDraftSourceRepository sourceRepository;
    @Autowired private SchemaDraftAnalysisRunRepository runRepository;
    @Autowired private SchemaDraftAggregateRevisionRepository aggregateRepository;
    @Autowired private SchemaDraftDecisionRepository decisionRepository;
    @Autowired private Neo4jPersistenceVersionBackfillService versionBackfillService;

    @BeforeEach
    void setUp() throws Exception {
        resetModelGate();
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
                "guidance":{"requiredConcepts":[{"name":"Person","identityKeys":["personId"]}]}}
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
        assertThat(completed.path("sourceOutcomes").get(0).path("reused").asBoolean()).isFalse();

        JsonNode retryAccepted = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/analysis-runs/{runId}/retry",
            "{\"revision\":1}", KNOWLEDGE_BASE_ID, draftId, firstRunId));
        JsonNode retryCompleted = awaitTerminal(draftId, retryAccepted.path("runId").asText());
        assertThat(retryCompleted.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(retryCompleted.path("sourceOutcomes").get(0).path("reused").asBoolean()).isTrue();

        JsonNode candidates = json(mockMvc.perform(get(
                "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/candidates",
                KNOWLEDGE_BASE_ID, draftId))
            .andExpect(status().isOk()).andReturn());
        assertThat(candidates.path("content").toString()).contains("EXISTING", "OBSERVED");
        String candidateIdentity = "node-property:Person:displayName";

        JsonNode decision = json(postJson(
            "/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}/decisions",
            """
                {"revision":1,"type":"ACCEPT","candidateIdentity":"%s","rationale":"reviewed"}
                """.formatted(candidateIdentity), KNOWLEDGE_BASE_ID, draftId));
        assertThat(decision.path("reviewState").asText()).isEqualTo("ACCEPTED");

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
        assertThat(secondCompleted.path("sourceOutcomes").get(0).path("reused").asBoolean()).isFalse();
        assertThat(decisionRepository.findByDraftIdOrderBySequenceAsc(draftId)).hasSize(1);

        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts/{draftId}",
                KNOWLEDGE_BASE_ID, draftId).param("revision", "3"))
            .andExpect(status().isNoContent());
        assertThat(draftRepository.findById(draftId)).isEmpty();
        assertThat(sourceRepository.findByDraftIdOrderByCreatedAtAsc(draftId)).isEmpty();
        assertThat(runRepository.findByDraftIdOrderByCreatedAtDesc(draftId)).isEmpty();
        assertThat(aggregateRepository.findByDraftIdOrderByRevisionDesc(draftId)).isEmpty();
        assertThat(output.getAll()).doesNotContain("private-draft-source", "private-guidance-value");
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
                .content("{\"revision\":1,\"guidance\":{\"domainDescription\":\"changed\"}}"))
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

    @TestConfiguration(proxyBeanMethods = false)
    static class DraftAiConfig {
        @Bean
        @Primary
        CandidateExtractionModelAdapter deterministicDraftCandidateExtractionModelAdapter() {
            return new CandidateExtractionModelAdapter(null, null) {
                @Override
                public CandidateExtractionResult extract(String promptWithoutFormat, String portablePrompt) {
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
    }
}
