package io.github.vfedoriv.graphrag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import io.github.vfedoriv.graphrag.ai.models.*;
import io.github.vfedoriv.graphrag.documents.adapters.model.SpringAiGraphExtractionClient;
import io.github.vfedoriv.graphrag.search.query.adapters.model.SpringAiCypherGenerationClient;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class NativeStructuredRequestTest {
    private static final String GRAPH = "{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[],\"relationships\":[]}";
    private static final String CYPHER = "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"RETURN $v LIMIT 5\",\"explanation\":\"MODEL_SENTINEL\",\"parameters\":[{\"key\":\"v\",\"value\":9007199254740993}]}";
    private static final SchemaDocument SCHEMA = new SchemaDocument("test", 1, "", List.of(), List.of(), List.of(), List.of());

    @Test void actualSdkRequestsUseStrictSchemasOnlyForEligibleNativeCalls(CapturedOutput output) throws Exception {
        try (Stub stub = new Stub()) {
            Clients nativeClients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA);
            stub.content = GRAPH;
            AiProfileContext.withProfile("profile", () -> nativeClients.graph.extract(SCHEMA, "SOURCE_SENTINEL"));
            stub.content = CYPHER;
            assertThat(AiProfileContext.withProfile("profile", () -> nativeClients.cypher.generate(SCHEMA, "PROMPT_SENTINEL", 5))
                .parameters().get("v")).isEqualTo(9007199254740993L);
            for (JsonNode request : stub.requests) {
                assertThat(request.path("model").asText()).isEqualTo("configured-model");
                assertThat(request.path("temperature").asDouble()).isEqualTo(0.25);
                assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_schema");
                assertThat(request.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
                assertThat(request.path("response_format").path("json_schema").path("schema").path("type").asText()).isEqualTo("object");
            }
            assertThat(stub.requests.getFirst().path("messages").toString()).contains("entry", "omit the relationship");
            assertThat(stub.requests.get(1).path("messages").toString()).contains("entry", "read-only");
            Clients portable = clients(stub, StructuredOutputMode.PORTABLE);
            stub.content = "{\"cypher\":\"RETURN 1\",\"explanation\":\"\",\"parameters\":{}}";
            AiProfileContext.withProfile("profile", () -> portable.cypher.generate(SCHEMA, "prompt", 5));
            assertThat(stub.requests.getLast().has("response_format")).isFalse();
            // Sharing the cached model never changes its defaults for other consumers.
            stub.content = "plain";
            nativeClients.model.call("unaffected workflow");
            assertThat(stub.requests.getLast().has("response_format")).isFalse();
            assertThat(output).doesNotContain("SOURCE_SENTINEL", "PROMPT_SENTINEL", "MODEL_SENTINEL");
        }
    }

    @Test void sdkRefusalsTruncationAndReasoningOnlyAreRejectedWithoutFallback(CapturedOutput output) throws Exception {
        try (Stub stub = new Stub()) {
            Clients clients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA);
            stub.content = GRAPH;
            stub.refusal = "REFUSAL_SENTINEL";
            rejectedGraph(clients, "MODEL_REFUSAL");
            stub.refusal = null;
            stub.finish = "length";
            rejectedGraph(clients, "INCOMPLETE_MODEL_OUTPUT");
            stub.finish = "content_filter";
            rejectedGraph(clients, "INCOMPLETE_MODEL_OUTPUT");
            stub.finish = "stop";
            stub.content = "";
            stub.reasoning = GRAPH + "REASONING_SENTINEL";
            rejectedGraph(clients, "EMPTY_MODEL_OUTPUT");
            stub.reasoning = null;
            stub.content = GRAPH + "PAYLOAD_SENTINEL trailing";
            rejectedGraph(clients, "INVALID_NATIVE_OUTPUT");
            assertThat(stub.requests).hasSize(5);
            assertThat(output).doesNotContain("REFUSAL_SENTINEL", "REASONING_SENTINEL", "PAYLOAD_SENTINEL", "SOURCE_SENTINEL");
        }
    }

    @Test void providerFormatErrorsAreClassifiedFromStructuredFieldsAndSdkRetriesRemain(CapturedOutput output) throws Exception {
        try (Stub stub = new Stub()) {
            Clients clients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA);
            stub.status = 400;
            stub.errorCode = "unsupported_response_format";
            rejectedGraph(clients, "NATIVE_FORMAT_REJECTED");
            stub.errorCode = "invalid_json_schema";
            rejectedGraph(clients, "NATIVE_FORMAT_REJECTED");
            stub.errorCode = "invalid_request_error";
            rejectedGraph(clients, "PROVIDER_FAILURE");
            stub.status = 401;
            rejectedGraph(clients, "PROVIDER_FAILURE");
            assertThat(stub.requests).hasSize(4);
            stub.status = 429;
            rejectedGraph(clients, "PROVIDER_FAILURE");
            // maxRetries=1 is configured on the model, no adapter retry is added.
            assertThat(stub.requests).hasSize(6);
            assertThat(stub.requests).allSatisfy(request -> assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_schema"));
            assertThat(output).doesNotContain("PROVIDER_MESSAGE_SENTINEL", "SOURCE_SENTINEL");
        }
    }

    @Test void discoveryAndSchemaGenerationShareNativeProfileWithoutNativeWireOptions() throws Exception {
        try (Stub stub = new Stub()) {
            Clients clients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA);
            ProfileScopedAiClientResolver resolver = ProfileScopedAiClientResolver.fromProviders(new EmptyObjectProvider<>(),
                provider(clients.model), provider(access(clients.model, StructuredOutputMode.NATIVE_JSON_SCHEMA)));
            io.github.vfedoriv.graphrag.schemas.discovery.adapters.model.CandidateExtractionModelAdapter candidates =
                new io.github.vfedoriv.graphrag.schemas.discovery.adapters.model.CandidateExtractionModelAdapter(resolver, TestAiObservationService.noop());
            stub.content = """
                {"nodes":[],"nodeProperties":[],"nodeKeys":[],"relationships":[],"relationshipProperties":[],"aliasSuggestions":[]}
                """;
            AiProfileContext.withProfile("profile", () -> candidates.extract("portable candidate prompt"));
            io.github.vfedoriv.graphrag.schemas.generation.adapters.model.SchemaGenerationModelAdapter generation =
                new io.github.vfedoriv.graphrag.schemas.generation.adapters.model.SchemaGenerationModelAdapter(resolver, TestAiObservationService.noop());
            stub.content = "plain example";
            assertThat(AiProfileContext.withProfile("profile", () -> generation.generateExample("example"))).isEqualTo("plain example");
            stub.content = "[]";
            AiProfileContext.withProfile("profile", () -> generation.extractGraph("source", "extract triples", "[]"));
            assertThat(stub.requests).hasSize(3).allSatisfy(request -> assertThat(request.has("response_format")).isFalse());
        }
    }

    @Test void nativeCypherRejectsRefusalTruncationEmptyAndMalformedResponses() throws Exception {
        try (Stub stub = new Stub()) {
            Clients clients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA);
            stub.content = CYPHER;
            stub.refusal = "refused";
            assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> clients.cypher.generate(SCHEMA, "prompt", 5)))
                .hasRootCauseMessage("MODEL_REFUSAL");
            stub.refusal = null; stub.finish = "length";
            assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> clients.cypher.generate(SCHEMA, "prompt", 5)))
                .hasRootCauseMessage("INCOMPLETE_MODEL_OUTPUT");
            stub.finish = "stop"; stub.content = ""; stub.reasoning = CYPHER;
            assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> clients.cypher.generate(SCHEMA, "prompt", 5)))
                .hasRootCauseMessage("EMPTY_MODEL_OUTPUT");
            stub.reasoning = null; stub.content = CYPHER + " trailing";
            assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> clients.cypher.generate(SCHEMA, "prompt", 5)))
                .hasRootCauseMessage("INVALID_NATIVE_OUTPUT");
            assertThat(stub.requests).hasSize(4);
        }
    }

    @Test void explicitlyNativeCustomClientFailsBeforeSendingAnyCall() {
        ChatModel model = prompt -> { throw new AssertionError("must not call an incapable model"); };
        AiModelAccess models = access(model, StructuredOutputMode.NATIVE_JSON_SCHEMA);
        SpringAiGraphExtractionClient client = new SpringAiGraphExtractionClient(provider(model), TestAiObservationService.noop(), provider(models));
        assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> client.extract(SCHEMA, "source")))
            .hasRootCauseMessage("NATIVE_FORMAT_UNAVAILABLE");
        SpringAiCypherGenerationClient missing = new SpringAiCypherGenerationClient(new EmptyObjectProvider<>(),
            TestAiObservationService.noop(), provider(access(null, StructuredOutputMode.NATIVE_JSON_SCHEMA)));
        assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> missing.generate(SCHEMA, "source", 5)))
            .hasRootCauseMessage("NATIVE_FORMAT_UNAVAILABLE");
    }

    @Test void nativeGraphRunsThroughNormalizationFilteringAndFatalLimitsBeforeWriting(CapturedOutput output) throws Exception {
        try (Stub stub = new Stub()) {
            Clients clients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA);
            SchemaDocument schema = new io.github.vfedoriv.graphrag.schemas.registry.application.SchemaParser().parse("""
                {"name":"contracts","version":1,"nodes":[
                  {"label":"Contract","key":"contractId","properties":[{"name":"contractId","type":"string"}]},
                  {"label":"Party","key":"name","properties":[{"name":"name","type":"string"}]}],
                 "relationships":[{"type":"HAS_PARTY","from":"Contract","to":"Party","properties":[]}]}
                """);
            io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshots schemas = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshots.class);
            org.mockito.Mockito.when(schemas.resolveActive("kb")).thenReturn(new io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot(
                "kb","schema",schema.name(),1,null,null,null,null,null,null,null,schema));
            io.github.vfedoriv.graphrag.documents.application.processing.ExtractionRunLifecycle lifecycle = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.documents.application.processing.ExtractionRunLifecycle.class);
            io.github.vfedoriv.graphrag.documents.domain.ExtractionRunNode run = new io.github.vfedoriv.graphrag.documents.domain.ExtractionRunNode();
            run.setId("run"); run.setSchemaId("schema");
            org.mockito.Mockito.when(lifecycle.start(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(run);
            org.mockito.Mockito.when(lifecycle.complete(run)).thenReturn(run);
            io.github.vfedoriv.graphrag.documents.ports.DocumentGraphWriter writer = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.documents.ports.DocumentGraphWriter.class);
            io.github.vfedoriv.graphrag.documents.ports.DocumentArtifactCleanup cleanup = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.documents.ports.DocumentArtifactCleanup.class);
            org.mockito.Mockito.when(cleanup.cleanupRunsAfterSuccessfulExtraction("doc", "run", false))
                .thenReturn(io.github.vfedoriv.graphrag.documents.ports.DocumentArtifactCleanup.ExtractionRunCleanupResult.zero());
            io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionService service =
                new io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionService(schemas, lifecycle,
                    new io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionValidationService(settings(3)),
                    writer, provider(clients.graph), cleanup, TestAiObservationService.noop());
            io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode document = new io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode();
            document.setId("doc"); document.setKnowledgeBaseId("kb");
            io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode chunk = new io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode();
            chunk.setId("chunk"); chunk.setDocumentId("doc"); chunk.setKnowledgeBaseId("kb"); chunk.setText("source");
            stub.content = """
                {"contractVersion":"graph-extraction-v1","nodes":[
                  {"label":"Contract","properties":[{"key":"contractId","value":"C-1"}],"confidence":null,"unknown":"ignored"},
                  {"label":"Party","properties":[{"key":"name","value":"Acme"}],"confidence":0.9},
                  {"label":"Unknown","properties":[{"key":"UNTRUSTED_KEY_SENTINEL","value":"PAYLOAD_SENTINEL"}],"confidence":null}],"relationships":[
                  {"type":"HAS_PARTY","fromLabel":"Contract","fromKey":[],"toLabel":"Party","toKey":[],"properties":[],"confidence":null},
                  {"type":"INVALID","fromLabel":"Contract","fromKey":[],"toLabel":"Party","toKey":[],"properties":[],"confidence":null},
                  {"type":"HAS_PARTY","fromLabel":"Contract","fromKey":[],"toLabel":"Unknown","toKey":[],"properties":[],"confidence":null}]}
                """;
            AiProfileContext.withProfile("profile", () -> service.extract(document, List.of(chunk), false));
            org.mockito.ArgumentCaptor<io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult> result =
                org.mockito.ArgumentCaptor.forClass(io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult.class);
            org.mockito.Mockito.verify(writer).write(org.mockito.ArgumentMatchers.eq("kb"), org.mockito.ArgumentMatchers.eq("run"),
                org.mockito.ArgumentMatchers.eq("schema"), org.mockito.ArgumentMatchers.eq("doc"), org.mockito.ArgumentMatchers.eq("chunk"), org.mockito.ArgumentMatchers.eq(schema), result.capture());
            assertThat(result.getValue().nodes()).hasSize(2);
            assertThat(result.getValue().relationships()).hasSize(1);
            assertThat(result.getValue().relationships().getFirst().fromKey()).containsEntry("contractId", "C-1");
            assertThat(result.getValue().relationships().getFirst().toKey()).containsEntry("name", "Acme");
            org.mockito.Mockito.clearInvocations(writer);
            io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionService bounded =
                new io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionService(schemas, lifecycle,
                    new io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionValidationService(settings(1)),
                    writer, provider(clients.graph), cleanup, TestAiObservationService.noop());
            assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> bounded.extract(document, List.of(chunk), false)))
                .isInstanceOf(io.github.vfedoriv.graphrag.documents.api.error.GraphExtractionValidationException.class);
            org.mockito.Mockito.verifyNoInteractions(writer);
            stub.refusal = "refused";
            assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> service.extract(document, List.of(chunk), false)))
                .hasRootCauseMessage("MODEL_REFUSAL");
            org.mockito.Mockito.verifyNoInteractions(writer);
            assertThat(output).doesNotContain("UNTRUSTED_KEY_SENTINEL", "PAYLOAD_SENTINEL");
        }
    }

    @Test void nativeCypherGenerationAndAskKeepSafetyPolicyAndNeverExecuteRejectedQueries() throws Exception {
        try (Stub stub = new Stub()) {
            Clients clients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA);
            SchemaDocument schema = new io.github.vfedoriv.graphrag.schemas.registry.application.SchemaParser().parse("""
                {"name":"contracts","version":1,"nodes":[{"label":"Contract","key":"contractId",
                    "properties":[{"name":"contractId","type":"string"}]}],"relationships":[]}
                """);
            io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas schemas = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas.class);
            org.mockito.Mockito.when(schemas.resolveActive("kb")).thenReturn(new io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot(
                "kb","schema",schema.name(),1,null,null,null,null,null,null,null,schema));
            io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles profiles = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles.class);
            org.mockito.Mockito.when(profiles.forKnowledgeBase("kb")).thenReturn(new io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles.Profile(
                "profile",1,"https://provider.invalid/v1","chat","embed",768,null));
            io.github.vfedoriv.graphrag.search.query.ports.QueryExecutor executor = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.search.query.ports.QueryExecutor.class);
            io.github.vfedoriv.graphrag.search.query.application.CypherValidationService validation = new io.github.vfedoriv.graphrag.search.query.application.CypherValidationService(settings(3), schemas, executor);
            io.github.vfedoriv.graphrag.search.query.application.CypherGenerationService generation = new io.github.vfedoriv.graphrag.search.query.application.CypherGenerationService(
                settings(3), schemas, provider(clients.cypher), validation, TestAiObservationService.noop(), profiles);
            io.github.vfedoriv.graphrag.search.query.application.CypherExecutionService execution = org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.search.query.application.CypherExecutionService.class);
            io.github.vfedoriv.graphrag.search.query.application.QueryAskService ask = new io.github.vfedoriv.graphrag.search.query.application.QueryAskService(
                generation, execution, TestAiObservationService.noop(), settings(3));
            for (String cypher : List.of("CREATE (n:Contract)", "MATCH (n:Unknown) RETURN n LIMIT 5", "MATCH (n:Contract) RETURN n LIMIT 999")) {
                stub.content = new ObjectMapper().writeValueAsString(Map.of("contractVersion", "cypher-generation-v1",
                    "cypher", cypher, "explanation", "", "parameters", List.of()));
                assertThat(generation.generate("kb", "query").validation().valid()).isFalse();
                assertThatThrownBy(() -> ask.ask("kb", "query"))
                    .isInstanceOf(io.github.vfedoriv.graphrag.search.query.api.error.QueryRejectedException.class);
                org.mockito.Mockito.verifyNoInteractions(execution);
            }
            stub.content = "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"MATCH (n:Contract) RETURN n LIMIT 5\",\"explanation\":\"\",\"parameters\":[]}";
            org.mockito.Mockito.doThrow(new IllegalArgumentException("planner failure")).when(executor)
                .explain(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());
            assertThatThrownBy(() -> ask.ask("kb", "query"))
                .isInstanceOf(io.github.vfedoriv.graphrag.search.query.api.error.QueryRejectedException.class);
            org.mockito.Mockito.verifyNoInteractions(execution);
            org.mockito.Mockito.verify(executor, org.mockito.Mockito.never()).execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());
            stub.refusal = "refused";
            assertThatThrownBy(() -> ask.ask("kb", "query")).hasRootCauseMessage("MODEL_REFUSAL");
            org.mockito.Mockito.verifyNoInteractions(execution);
        }
    }

    private io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess settings(int maxEntities) {
        return io.github.vfedoriv.graphrag.TestRuntimeSettings.from(properties(maxEntities));
    }
    private io.github.vfedoriv.graphrag.bootstrap.AppProperties properties(int maxEntities) {
        return new io.github.vfedoriv.graphrag.bootstrap.AppProperties(
            new io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties("neo4j"),
            new io.github.vfedoriv.graphrag.ai.configuration.ModelProperties("https://provider.invalid/v1", "", "embed", 768, "chat"),
            new io.github.vfedoriv.graphrag.storage.configuration.StorageProperties(java.nio.file.Path.of("var/documents")),
            new io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties(800,80,4000),
            new io.github.vfedoriv.graphrag.settings.configuration.QueryProperties(200,15,true,List.of("CREATE")),
            new io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties(maxEntities,80,2));
    }

    @Test void emptySdkChoicesFailWithoutLoggingThePromptUnderApplicationLoggingPolicy(CapturedOutput output) throws Exception {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.InputStream input = getClass().getResourceAsStream("/application.properties")) { properties.load(input); }
        ch.qos.logback.classic.Logger sdkLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OpenAiChatModel.class);
        ch.qos.logback.classic.Level previous = sdkLogger.getLevel();
        String configured = properties.getProperty("logging.level.org.springframework.ai.openai.OpenAiChatModel");
        sdkLogger.setLevel(ch.qos.logback.classic.Level.toLevel(configured == null ? properties.getProperty("logging.level.root") : configured));
        try (Stub stub = new Stub()) {
            stub.emptyChoices = true;
            rejectedGraph(clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA), "EMPTY_MODEL_OUTPUT");
            assertThat(output).doesNotContain("SOURCE_SENTINEL", "contractVersion", "No choices returned for prompt");
        } finally { sdkLogger.setLevel(previous); }
    }

    @Test void observationsRecordEffectiveModeContractAndSafeOutcome() throws Exception {
        try (Stub stub = new Stub()) {
            io.micrometer.observation.ObservationRegistry registry = io.micrometer.observation.ObservationRegistry.create();
            List<io.micrometer.observation.Observation.Context> stopped = new ArrayList<>();
            registry.observationConfig().observationHandler(new io.micrometer.observation.ObservationHandler<io.micrometer.observation.Observation.Context>() {
                public boolean supportsContext(io.micrometer.observation.Observation.Context context) { return true; }
                public void onStop(io.micrometer.observation.Observation.Context context) { stopped.add(context); }
            });
            io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties config =
                new io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties(true, false, 512, false, 512, true, true);
            io.github.vfedoriv.graphrag.bootstrap.AppProperties props = properties(3);
            io.github.vfedoriv.graphrag.observability.AiObservationService observations = new io.github.vfedoriv.graphrag.observability.AiObservationService(
                config, registry, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                io.github.vfedoriv.graphrag.TestRuntimeSettings.modelMetadata(props), new org.springframework.mock.env.MockEnvironment(),
                io.github.vfedoriv.graphrag.TestRuntimeSettings.from(props, config), new EmptyObjectProvider<>());
            Clients clients = clients(stub, StructuredOutputMode.NATIVE_JSON_SCHEMA, observations);
            AiProfileContext.withProfile("profile", () -> clients.graph.extract(SCHEMA, "SOURCE_SENTINEL"));
            stub.refusal = "REFUSAL_SENTINEL";
            rejectedGraph(clients, "MODEL_REFUSAL");
            assertThat(stopped).hasSize(2);
            assertThat(stopped.get(0).getLowCardinalityKeyValue("ai.output.mode").getValue()).isEqualTo("NATIVE_JSON_SCHEMA");
            assertThat(stopped.get(0).getLowCardinalityKeyValue("ai.output.contract").getValue()).isEqualTo("graph-extraction-v1");
            assertThat(stopped.get(0).getLowCardinalityKeyValue("ai.output.outcome").getValue()).isEqualTo("SUCCESS");
            assertThat(stopped.get(1).getLowCardinalityKeyValue("ai.output.outcome").getValue()).isEqualTo("MODEL_REFUSAL");
            assertThat(stopped.get(1).getError().getMessage()).isEqualTo("MODEL_REFUSAL");
            assertThat(stopped.get(1).getError().getCause()).isNull();
        }
    }

    private void rejectedGraph(Clients clients, String category) {
        assertThatThrownBy(() -> AiProfileContext.withProfile("profile", () -> clients.graph.extract(SCHEMA, "SOURCE_SENTINEL")))
            .isInstanceOf(IllegalArgumentException.class).hasRootCauseMessage(category);
    }
    private Clients clients(Stub stub, StructuredOutputMode mode) {
        return clients(stub, mode, TestAiObservationService.noop());
    }
    private Clients clients(Stub stub, StructuredOutputMode mode, io.github.vfedoriv.graphrag.observability.AiObservationService observations) {
        ChatModel model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
            .baseUrl("http://127.0.0.1:" + stub.server.getAddress().getPort() + "/v1")
            .apiKey("test-key").model("configured-model").temperature(0.25)
            .timeout(Duration.ofSeconds(3)).maxRetries(1).build()).build();
        AiModelAccess models = access(model, mode);
        return new Clients(new SpringAiGraphExtractionClient(provider(model), observations, provider(models)),
            new SpringAiCypherGenerationClient(provider(model), observations, provider(models)), model);
    }
    private AiModelAccess access(ChatModel model, StructuredOutputMode mode) {
        return new AiModelAccess() {
            public ChatModel chatModel(String profileId) { return model; }
            public ResolvedChatBinding chatBinding(String profileId) { return new ResolvedChatBinding(model, mode, profileId, 1L); }
            public EmbeddingModel embeddingModel(String profileId) { throw new UnsupportedOperationException(); }
        };
    }
    private <T> ObjectProvider<T> provider(T value) { return new ObjectProvider<>() {
        public T getIfAvailable() { return value; }
        public java.util.stream.Stream<T> orderedStream() { return java.util.stream.Stream.of(value); }
    }; }
    private record Clients(SpringAiGraphExtractionClient graph, SpringAiCypherGenerationClient cypher, ChatModel model) { }
    private static class Stub implements AutoCloseable {
        final HttpServer server;
        final ObjectMapper mapper = new ObjectMapper();
        final List<JsonNode> requests = new ArrayList<>();
        boolean emptyChoices;
        String content = GRAPH;
        String refusal;
        String reasoning;
        String finish = "stop";
        int status = 200;
        String errorCode;
        Stub() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                requests.add(mapper.readTree(exchange.getRequestBody()));
                Map<String, Object> message = new java.util.HashMap<>();
                message.put("role", "assistant"); message.put("content", content);
                if (refusal != null) message.put("refusal", refusal);
                if (reasoning != null) message.put("reasoning_content", reasoning);
                Object body = status == 200 ? Map.of("id", "stub", "object", "chat.completion", "created", 1, "model", "configured-model",
                    "choices", emptyChoices ? List.of() : List.of(Map.of("index", 0, "message", message, "finish_reason", finish)),
                    "usage", Map.of("prompt_tokens", 7, "completion_tokens", 11, "total_tokens", 18))
                    : Map.of("error", Map.of("code", errorCode, "type", "invalid_request_error", "message", "PROVIDER_MESSAGE_SENTINEL"));
                byte[] bytes = mapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            }); server.start();
        }
        public void close() { server.stop(0); }
    }
}
