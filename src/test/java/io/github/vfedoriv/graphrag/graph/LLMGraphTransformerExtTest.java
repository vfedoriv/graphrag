package io.github.vfedoriv.graphrag.graph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LLMGraphTransformerExtTest {

    @Test
    void createUnstructuredPrompt_requiresDescriptionsAndUsefulProperties() {
        LLMGraphTransformerExt transformer = new LLMGraphTransformerExt(
            fixedModel("[]"),
            List.of("Contract", "Party"),
            List.of("HAS_PARTY"),
            null,
            "Prefer legal contract terminology.",
            "[]",
            1
        );

        List<ChatMessage> messages = transformer.createUnstructuredPrompt("Contract A names Acme as supplier.");

        assertThat(messages).hasSize(2);
        SystemMessage systemMessage = (SystemMessage) messages.get(0);
        UserMessage userMessage = (UserMessage) messages.get(1);
        assertThat(systemMessage.text())
            .contains("'head_properties'", "'tail_properties'", "'relation_properties'")
            .contains("must include a non-empty 'description' property")
            .contains("Add useful domain properties")
            .contains("The 'head_type' and 'tail_type' must be one of: [Contract, Party]")
            .contains("The 'relation' must be one of: [HAS_PARTY]");
        assertThat(userMessage.singleText())
            .contains("\"head_properties\": {\"description\": [\"...\"]")
            .contains("\"relation_properties\": {\"description\": [\"...\"]")
            .contains("infer useful node and relationship properties plus non-empty descriptions")
            .contains("Prefer legal contract terminology.")
            .contains("Contract A names Acme as supplier.");
    }

    @Test
    void transform_preservesNodeAndEdgeProperties(CapturedOutput output) {
        String payload = """
            [
              {
                "head": "Contract A",
                "head_type": "Contract",
                "head_properties": {"contractId": ["C-1"], "tags": ["msa", "2026"]},
                "relation": "HAS_PARTY",
                "relation_properties": {"description": ["Counterparty relationship"], "role": ["supplier"]},
                "tail": "Acme Corp",
                "tail_type": "Party",
                "tail_properties": {"name": ["Acme Corp"]}
              }
            ]
            """;
        LLMGraphTransformerExt transformer = new LLMGraphTransformerExt(
            fixedModel("```json\n" + payload + "\n```"),
            List.of(),
            List.of(),
            null,
            "",
            "[]",
            1
        );

        GraphDocument graph = transformer.transform(Document.from("text"));

        assertThat(graph).isNotNull();
        assertThat(graph.nodes()).hasSize(2);
        GraphNode contract = graph.nodes().stream().filter(node -> node.id().equals("Contract A")).findFirst().orElseThrow();
        assertThat(contract.properties()).containsEntry("contractId", "C-1");
        assertThat(contract.properties()).containsEntry("tags", "[\"msa\",\"2026\"]");

        GraphEdge edge = graph.relationships().stream().findFirst().orElseThrow();
        assertThat(edge.properties()).containsEntry("description", "Counterparty relationship");
        assertThat(edge.properties()).containsEntry("role", "supplier");
        assertThat(output).contains("LLM graph transformer raw response attempt 1");
        assertThat(output).contains("\"head_properties\": {\"contractId\": [\"C-1\"], \"tags\": [\"msa\", \"2026\"]}");
        assertThat(output).contains("LLM graph transformer parsed response attempt 1");
        assertThat(output).contains("headProperties={");
        assertThat(output).contains("relationProperties={");
        assertThat(output).contains("nonEmpty=true");
    }

    @Test
    void transform_handlesMissingProperties() {
        String payload = """
            [
              {
                "head": "WO-1",
                "head_type": "WorkOrder",
                "relation": "ASSIGNED_TO",
                "tail": "Tech-1",
                "tail_type": "Technician"
              }
            ]
            """;
        LLMGraphTransformerExt transformer = new LLMGraphTransformerExt(
            fixedModel(payload),
            List.of(),
            List.of(),
            null,
            "",
            "[]",
            1
        );

        GraphDocument graph = transformer.transform(Document.from("text"));

        assertThat(graph).isNotNull();
        assertThat(graph.nodes()).allSatisfy(node -> assertThat(node.properties()).isEmpty());
        assertThat(graph.relationships()).allSatisfy(rel -> assertThat(rel.properties()).isEmpty());
    }

    private static ChatModel fixedModel(String responseText) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder().aiMessage(AiMessage.from(responseText)).build();
            }
        };
    }
}
