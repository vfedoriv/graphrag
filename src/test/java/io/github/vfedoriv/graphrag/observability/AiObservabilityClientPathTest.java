package io.github.vfedoriv.graphrag.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.embedding.SpringAiEmbeddingClient;
import io.github.vfedoriv.graphrag.graph.SpringAiGraphExtractionClient;
import io.github.vfedoriv.graphrag.llm.SpringAiLangChain4jChatModelAdapter;
import io.github.vfedoriv.graphrag.query.SpringAiCypherGenerationClient;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.service.LangChain4jSchemaGenerationService;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

class AiObservabilityClientPathTest {

    @Test
    void graphExtractionPopulatesLangfuseInputOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        AiObservationService service = service(handler);
        SpringAiGraphExtractionClient client = new SpringAiGraphExtractionClient(
            provider(chatModel("{\"nodes\":[],\"relationships\":[]}")),
            service
        );

        try (AiObservationScope ignored = service.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_GRAPH_EXTRACTION,
            "contracts",
            Map.of()
        ))) {
            client.extract(schema(), "Contract C-1 has party Acme");
        }

        assertModelObservationHasInputOutput(handler);
        assertWorkflowObservationHasTraceInputOutput(handler);
    }

    @Test
    void graphExtractionParseFailureStillPopulatesLangfuseOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        AiObservationService service = service(handler);
        SpringAiGraphExtractionClient client = new SpringAiGraphExtractionClient(
            provider(chatModel("not valid json")),
            service
        );

        assertThatThrownBy(() -> {
            try (AiObservationScope ignored = service.startWorkflow(new AiWorkflowContext(
                AiObservationService.WORKFLOW_GRAPH_EXTRACTION,
                "contracts",
                Map.of()
            ))) {
                client.extract(schema(), "Contract C-1 has party Acme");
            }
        }).isInstanceOf(IllegalArgumentException.class);

        CapturedObservation modelObservation = assertModelObservationHasInputOutput(handler);
        assertThat(modelObservation.highCardinalityAttributes().get("langfuse.observation.output"))
            .isEqualTo("not valid json");
    }

    @Test
    void cypherGenerationPopulatesLangfuseInputOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        AiObservationService service = service(handler);
        SpringAiCypherGenerationClient client = new SpringAiCypherGenerationClient(
            provider(chatModel("{\"cypher\":\"MATCH (n) RETURN n LIMIT 1\",\"explanation\":\"ok\",\"parameters\":{}}")),
            service
        );

        try (AiObservationScope ignored = service.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            "contracts",
            Map.of()
        ))) {
            client.generate(schema(), "show contracts", 10);
        }

        assertModelObservationHasInputOutput(handler);
        assertWorkflowObservationHasTraceInputOutput(handler);
    }

    @Test
    void cypherGenerationParseFailureStillPopulatesLangfuseOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        AiObservationService service = service(handler);
        SpringAiCypherGenerationClient client = new SpringAiCypherGenerationClient(
            provider(chatModel("```cypher\nMATCH (n) RETURN n\n```")),
            service
        );

        assertThatThrownBy(() -> {
            try (AiObservationScope ignored = service.startWorkflow(new AiWorkflowContext(
                AiObservationService.WORKFLOW_CYPHER_GENERATION,
                "contracts",
                Map.of()
            ))) {
                client.generate(schema(), "show contracts", 10);
            }
        }).isInstanceOf(IllegalArgumentException.class);

        CapturedObservation modelObservation = assertModelObservationHasInputOutput(handler);
        assertThat(modelObservation.highCardinalityAttributes().get("langfuse.observation.output"))
            .contains("MATCH (n) RETURN n");
    }

    @Test
    void embeddingPopulatesLangfuseInputOutputWithSummaryOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        AiObservationService service = service(handler);
        SpringAiEmbeddingClient client = new SpringAiEmbeddingClient(
            provider(embeddingModel()),
            service
        );

        try (AiObservationScope ignored = service.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_DOCUMENT_PROCESSING,
            null,
            Map.of()
        ))) {
            client.embed(List.of("first chunk", "second chunk"));
        }

        CapturedObservation modelObservation = assertModelObservationHasInputOutput(handler);
        assertThat(modelObservation.highCardinalityAttributes().get("langfuse.observation.output"))
            .contains("Embedding response with 2 vectors and first vector size 2");
        assertWorkflowObservationHasTraceInputOutput(handler);
    }

    @Test
    void schemaExampleGenerationPopulatesLangfuseInputOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        AiObservationService service = service(handler);
        LangChain4jSchemaGenerationService schemaGenerationService = new LangChain4jSchemaGenerationService(
            provider(chatModel("[]")),
            service
        );

        schemaGenerationService.generateExample("Acme supplies contract C-1", "focus on parties");

        assertModelObservationHasInputOutput(handler);
    }

    @Test
    void langChain4jChatAdapterPopulatesLangfuseInputOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        AiObservationService service = service(handler);
        SpringAiLangChain4jChatModelAdapter adapter = new SpringAiLangChain4jChatModelAdapter(
            chatModel("{\"head\":\"Acme\",\"head_type\":\"Party\",\"relation\":\"SUPPLIES\",\"tail\":\"C-1\",\"tail_type\":\"Contract\"}"),
            service
        );

        adapter.doChat(ChatRequest.builder()
            .messages(UserMessage.from("Generate schema relationships"))
            .build());

        assertModelObservationHasInputOutput(handler);
    }

    private CapturedObservation assertModelObservationHasInputOutput(CapturingObservationHandler handler) {
        CapturedObservation observation = handler.singleObservationNamed("graphrag.ai.model");
        assertThat(observation.highCardinalityAttributes())
            .containsKeys(
                "langfuse.observation.type",
                "langfuse.observation.model.name",
                "model",
                "langfuse.observation.input",
                "langfuse.observation.output",
                "langfuse.trace.input",
                "langfuse.trace.output"
            );
        assertThat(observation.highCardinalityAttributes())
            .containsEntry("langfuse.observation.type", "generation")
            .containsEntry("model", observation.highCardinalityAttributes().get("langfuse.observation.model.name"));
        assertThat(observation.highCardinalityAttributes().get("langfuse.observation.model.name")).isNotBlank();
        assertThat(observation.highCardinalityAttributes().get("langfuse.observation.input")).isNotBlank();
        assertThat(observation.highCardinalityAttributes().get("langfuse.observation.output")).isNotBlank();
        return observation;
    }

    private void assertWorkflowObservationHasTraceInputOutput(CapturingObservationHandler handler) {
        CapturedObservation observation = handler.singleObservationNamed("graphrag.ai.workflow");
        assertThat(observation.highCardinalityAttributes())
            .containsKeys("langfuse.trace.input", "langfuse.trace.output")
            .doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output");
        assertThat(observation.highCardinalityAttributes().get("langfuse.trace.input")).isNotBlank();
        assertThat(observation.highCardinalityAttributes().get("langfuse.trace.output")).isNotBlank();
    }

    private AiObservationService service(CapturingObservationHandler handler) {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(handler);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.ai.model.chat", "openai")
            .withProperty("spring.ai.model.embedding", "openai");
        return new AiObservationService(
            new AiObservabilityProperties(true, false, 128, true, 512, true, true),
            observationRegistry,
            new SimpleMeterRegistry(),
            appProperties(),
            environment
        );
    }

    private AppProperties appProperties() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE")),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private ObjectProvider<org.springframework.ai.chat.model.ChatModel> provider(org.springframework.ai.chat.model.ChatModel model) {
        return new ObjectProvider<>() {
            @Override
            public org.springframework.ai.chat.model.ChatModel getIfAvailable() {
                return model;
            }
        };
    }

    private ObjectProvider<EmbeddingModel> provider(EmbeddingModel model) {
        return new ObjectProvider<>() {
            @Override
            public EmbeddingModel getIfAvailable() {
                return model;
            }
        };
    }

    private org.springframework.ai.chat.model.ChatModel chatModel(String content) {
        return new org.springframework.ai.chat.model.ChatModel() {
            @Override
            public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
                return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(new AssistantMessage(content))));
            }
        };
    }

    private EmbeddingModel embeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return new EmbeddingResponse(List.of(
                    new Embedding(new float[] {0.1F, 0.2F}, 0),
                    new Embedding(new float[] {0.3F, 0.4F}, 1)
                ));
            }

            @Override
            public float[] embed(Document document) {
                return new float[] {0.1F, 0.2F};
            }
        };
    }

    private SchemaDocument schema() {
        return new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition("Contract", "", List.of("contractId"), List.of()),
                new SchemaDocument.NodeDefinition("Party", "", List.of("name"), List.of())
            ),
            List.of(new SchemaDocument.RelationshipDefinition("HAS_PARTY", "Contract", "Party", "", List.of())),
            List.of(),
            List.of()
        );
    }

    private static final class CapturingObservationHandler implements ObservationHandler<Observation.Context> {

        private final List<CapturedObservation> observations = new ArrayList<>();

        @Override
        public void onStop(Observation.Context context) {
            Map<String, String> highCardinalityAttributes = new HashMap<>();
            for (KeyValue keyValue : context.getHighCardinalityKeyValues()) {
                highCardinalityAttributes.put(keyValue.getKey(), keyValue.getValue());
            }
            observations.add(new CapturedObservation(context.getName(), highCardinalityAttributes));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        private CapturedObservation singleObservationNamed(String name) {
            List<CapturedObservation> matches = observations.stream()
                .filter(observation -> name.equals(observation.name()))
                .toList();
            assertThat(matches).hasSize(1);
            return matches.getFirst();
        }
    }

    private record CapturedObservation(String name, Map<String, String> highCardinalityAttributes) {
    }
}
