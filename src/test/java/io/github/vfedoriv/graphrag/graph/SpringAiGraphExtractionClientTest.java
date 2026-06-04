package io.github.vfedoriv.graphrag.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiGraphExtractionClientTest {

    @Test
    void extract_ignoresUnknownFieldsInModelResponse() {
        String modelJson = """
            {
              "nodes": [
                {
                  "label": "Contract",
                  "id": "contract-c-1",
                  "properties": {"contractId": "C-1"},
                  "confidence": 0.93
                },
                {
                  "label": "Party",
                  "extra": "ignored",
                  "properties": {"name": "Acme"},
                  "confidence": 0.82
                }
              ],
              "relationships": [
                {
                  "type": "HAS_PARTY",
                  "fromLabel": "Contract",
                  "fromKey": {"contractId": "C-1"},
                  "toLabel": "Party",
                  "toKey": {"name": "Acme"},
                  "properties": {"role": "Supplier"},
                  "confidence": 0.77,
                  "unknownRelField": "ignored"
                }
              ]
            }
            """;

        SpringAiGraphExtractionClient client = new SpringAiGraphExtractionClient(provider(chatModel(modelJson)), TestAiObservationService.noop());
        GraphExtractionResult result = client.extract(schema(), "Contract C-1 has party Acme");

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.nodes().get(0).label()).isEqualTo("Contract");
        assertThat(result.nodes().get(0).properties()).containsEntry("contractId", "C-1");
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().get(0).type()).isEqualTo("HAS_PARTY");
    }

    @Test
    void validationStillFailsForSchemaViolationWithUnknownFieldsPresent() {
        String modelJson = """
            {
              "nodes": [
                {
                  "label": "UnknownLabel",
                  "id": "u-1",
                  "properties": {"id": "u-1"},
                  "confidence": 0.9,
                  "modelHint": "extra"
                }
              ],
              "relationships": []
            }
            """;

        SpringAiGraphExtractionClient client = new SpringAiGraphExtractionClient(provider(chatModel(modelJson)), TestAiObservationService.noop());
        GraphExtractionResult parsed = client.extract(schema(), "bad node label");
        GraphExtractionValidationService validationService = new GraphExtractionValidationService(
            new AppProperties(
                new AppProperties.Neo4j("neo4j"),
                new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
                new AppProperties.Storage(Path.of("var/documents")),
                new AppProperties.Chunking(800, 80, 4000),
                new AppProperties.Query(200, 15, true, List.of("CREATE")),
                new AppProperties.Extraction(40, 80, 2)
            )
        );

        GraphExtractionResult validated = validationService.validate(parsed, schema());

        assertThat(validated.nodes()).isEmpty();
        assertThat(validated.relationships()).isEmpty();
    }

    @Test
    void extract_promptContainsAllowedRelationshipTriplesAndOmitRule() {
        String modelJson = "{\"nodes\":[],\"relationships\":[]}";
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                capturedPrompt.set(extractPromptText(prompt));
                return new ChatResponse(List.of(new Generation(new AssistantMessage(modelJson))));
            }
        };

        SpringAiGraphExtractionClient client = new SpringAiGraphExtractionClient(provider(model), TestAiObservationService.noop());
        client.extract(schema(), "source text");

        assertThat(capturedPrompt.get()).contains("Allowed relationship triples (type|fromLabel|toLabel):");
        assertThat(capturedPrompt.get()).contains("HAS_PARTY|Contract|Party");
        assertThat(capturedPrompt.get()).contains("If no listed relationship triple applies, omit the relationship.");
    }

    private ObjectProvider<ChatModel> provider(ChatModel model) {
        return new ObjectProvider<>() {
            @Override
            public ChatModel getIfAvailable() {
                return model;
            }
        };
    }

    private ChatModel chatModel(String modelJson) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(modelJson))));
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

    private String extractPromptText(Prompt prompt) {
        try {
            Object contents = Prompt.class.getMethod("getContents").invoke(prompt);
            if (contents instanceof List<?> list && !list.isEmpty()) {
                Object first = list.getFirst();
                Object text = first.getClass().getMethod("getText").invoke(first);
                if (text instanceof String value) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // Fallback to Prompt.toString() when internals change.
        }
        return prompt.toString();
    }
}
