package io.github.vfedoriv.graphrag.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.service.EmptyObjectProvider;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SpringAiCypherGenerationClientTest {

    @Test
    void generateDoesNotLogPromptOrModelResponse(CapturedOutput output) {
        String promptSentinel = "QUERY_PROMPT_SENTINEL";
        String responseSentinel = "QUERY_RESPONSE_SENTINEL";
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "{\"cypher\":\"MATCH (n:Contract) RETURN n LIMIT 10\",\"explanation\":\"" + responseSentinel + "\",\"parameters\":{}}"
                ))));
            }
        };
        SpringAiCypherGenerationClient client = new SpringAiCypherGenerationClient(
            provider(model),
            TestAiObservationService.noop(),
            new EmptyObjectProvider<>()
        );

        GeneratedCypher generated = client.generate(schema(), promptSentinel, 10);

        assertThat(generated.explanation()).isEqualTo(responseSentinel);
        assertThat(output)
            .contains("requestFingerprint=sha256:", "responseFingerprint=sha256:")
            .doesNotContain(promptSentinel, responseSentinel);
    }

    private ObjectProvider<ChatModel> provider(ChatModel model) {
        return new ObjectProvider<>() {
            @Override
            public ChatModel getIfAvailable() {
                return model;
            }

            @Override
            public Stream<ChatModel> orderedStream() {
                return Stream.of(model);
            }
        };
    }

    private SchemaDocument schema() {
        return new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(new SchemaDocument.NodeDefinition("Contract", "", List.of("contractId"), List.of())),
            List.of(),
            List.of(),
            List.of()
        );
    }
}
