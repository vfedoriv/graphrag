package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaKnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.AdvancedSearchRunRepository;
import io.github.vfedoriv.graphrag.service.AdvancedSearchResultCodec;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRunMaintenance;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRunProcessor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AdvancedSearchRunIntegrationTest.ProcessorConfiguration.class})
@IntegrationTest
@TestPropertySource(properties = {
    "app.advanced-search.concurrency=1",
    "app.advanced-search.queue-capacity=1",
    "app.advanced-search.branch-concurrency=1"
})
class AdvancedSearchRunIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AdvancedSearchRunRepository runRepository;
    @Autowired private AdvancedSearchRunMaintenance maintenance;
    @Autowired private JpaKnowledgeBaseRepository knowledgeBaseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void reset() throws Exception {
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        createKnowledgeBase("kb-runs");
        createKnowledgeBase("kb-other");
    }

    @Test
    void endpointsEnforceAdmissionOwnershipPagingConflictAndIdempotentCancellation() throws Exception {
        String first = submit("kb-runs", "first durable query");
        awaitStatus(first, AdvancedSearchRunStatus.RUNNING);
        String second = submit("kb-runs", "second durable query");

        mockMvc.perform(post("/api/v1/knowledge-bases/{kb}/queries/advanced-search-runs", "kb-runs")
                .contentType("application/json").content("{\"query\":\"capacity rejected\"}"))
            .andExpect(status().isTooManyRequests());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.advanced_search_run WHERE knowledge_base_id = 'kb-runs'", Integer.class))
            .isEqualTo(2);

        mockMvc.perform(get("/api/v1/knowledge-bases/{kb}/queries/advanced-search-runs/{id}/result", "kb-runs", first))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.runStatus").value("RUNNING"))
            .andExpect(jsonPath("$.runStage").exists());
        mockMvc.perform(get("/api/v1/knowledge-bases/{kb}/queries/advanced-search-runs", "kb-runs")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/knowledge-bases/{kb}/queries/advanced-search-runs/{id}", "kb-other", first))
            .andExpect(status().isNotFound());

        cancel(first).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationRequested").value(true));
        cancel(first).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationRequested").value(true));
        cancel(second).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationRequested").value(true));
        awaitStatus(first, AdvancedSearchRunStatus.CANCELLED);
        awaitStatus(second, AdvancedSearchRunStatus.CANCELLED);
    }

    @Test
    void postgresClaimsRecoveryRetentionAndKnowledgeBaseCascadesAreDurable() throws Exception {
        AdvancedSearchRunNode claimable = queued("claimable", "kb-runs", Instant.now().plusSeconds(60));
        runRepository.save(claimable);
        assertThat(runRepository.claim(claimable.getId(), "worker-a", Instant.now())).isTrue();
        assertThat(runRepository.claim(claimable.getId(), "worker-b", Instant.now())).isFalse();

        AdvancedSearchRunNode interrupted = queued("interrupted", "kb-runs", Instant.now().plusSeconds(60));
        runRepository.save(interrupted);
        maintenance.run(null);
        assertThat(runRepository.findById(interrupted.getId()).orElseThrow().getStatus())
            .isEqualTo(AdvancedSearchRunStatus.INTERRUPTED);

        insertTerminal("expired", "kb-runs", Instant.now().minusSeconds(1));
        jdbcTemplate.update("""
            INSERT INTO app.advanced_search_result(run_id,payload_version,result_json,evidence_count,created_at,version)
            VALUES ('expired',1,CAST(? AS jsonb),0,now(),0)
            """, "{\"payloadVersion\":1,\"evidence\":[]}");
        assertThat(maintenance.cleanup()).isGreaterThanOrEqualTo(1);
        assertThat(runRepository.findById("expired")).isEmpty();

        insertTerminal("cascade", "kb-other", Instant.now().plusSeconds(3600));
        jdbcTemplate.update("""
            INSERT INTO app.advanced_search_attempt
              (id,run_id,round_number,retriever,status,candidate_count,latency_ms,created_at,completed_at,version)
            VALUES ('attempt-cascade','cascade',1,'DENSE','COMPLETED',0,1,now(),now(),0)
            """);
        jdbcTemplate.update("""
            INSERT INTO app.advanced_search_result(run_id,payload_version,result_json,evidence_count,created_at,version)
            VALUES ('cascade',1,CAST(? AS jsonb),0,now(),0)
            """, "{\"payloadVersion\":1,\"evidence\":[]}");
        knowledgeBaseRepository.deleteById("kb-other");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.advanced_search_run WHERE id = 'cascade'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.advanced_search_attempt WHERE run_id = 'cascade'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.advanced_search_result WHERE run_id = 'cascade'", Integer.class)).isZero();
    }

    private void createKnowledgeBase(String id) throws Exception {
        mockMvc.perform(post("/api/v1/knowledge-bases").contentType("application/json")
            .content("{\"id\":\"" + id + "\",\"name\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    private String submit(String knowledgeBaseId, String query) throws Exception {
        String body = mockMvc.perform(post("/api/v1/knowledge-bases/{kb}/queries/advanced-search-runs", knowledgeBaseId)
                .contentType("application/json").content("{\"query\":\"" + query + "\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.links.self").exists())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions cancel(String id) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/knowledge-bases/{kb}/queries/advanced-search-runs/{id}/cancel", "kb-runs", id));
    }

    private void awaitStatus(String id, AdvancedSearchRunStatus expected) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            AdvancedSearchRunStatus actual = runRepository.findById(id).orElseThrow().getStatus();
            if (actual == expected) { return; }
            Thread.sleep(10L);
        }
        throw new AssertionError("run did not reach " + expected);
    }

    private AdvancedSearchRunNode queued(String id, String knowledgeBaseId, Instant deadline) {
        AdvancedSearchRunNode run = new AdvancedSearchRunNode(); run.setId(id); run.setKnowledgeBaseId(knowledgeBaseId);
        run.setQueryText("query"); run.setStatus(AdvancedSearchRunStatus.QUEUED); run.setStage(AdvancedSearchRunStage.QUEUED);
        run.setRequestedEvidence(10); run.setIncludeEvidenceText(false); run.setSettingsSnapshotJson("{}");
        run.setCompletedBranches(0); run.setTotalBranches(3); run.setEvidenceCount(0); run.setDeadlineAt(deadline);
        run.setCreatedAt(Instant.now()); return run;
    }

    private void insertTerminal(String id, String knowledgeBaseId, Instant expiresAt) {
        jdbcTemplate.update("""
            INSERT INTO app.advanced_search_run
              (id,knowledge_base_id,query_text,status,stage,requested_evidence,include_evidence_text,
               settings_snapshot_json,completed_branches,total_branches,evidence_count,deadline_at,created_at,
               completed_at,expires_at,version)
            VALUES (?,?,?,'COMPLETED','TERMINAL',10,false,CAST(? AS jsonb),3,3,0,now(),now(),now(),?,0)
            """, id, knowledgeBaseId, "query", "{}", java.sql.Timestamp.from(expiresAt));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProcessorConfiguration {
        @Bean
        @Primary
        AdvancedSearchRunProcessor blockingProcessor(ObjectMapper objectMapper) {
            return context -> {
                while (!context.cancelled().getAsBoolean() && Instant.now().isBefore(context.deadline())) {
                    try { Thread.sleep(10L); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
                }
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("payloadVersion", AdvancedSearchResultCodec.PAYLOAD_VERSION); payload.putArray("evidence");
                List<Result> attempts = List.of(
                    result(Branch.DENSE), result(Branch.LEXICAL), result(Branch.METADATA));
                return new AdvancedSearchRunProcessor.ProcessingResult(payload, 0, attempts, 3);
            };
        }
        private Result result(Branch branch) {
            return new Result(branch, List.of(), new Diagnostics(Status.COMPLETED, 1, 0, null));
        }
    }
}
