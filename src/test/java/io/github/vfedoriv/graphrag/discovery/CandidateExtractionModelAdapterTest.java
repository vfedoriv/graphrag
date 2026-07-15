package io.github.vfedoriv.graphrag.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

class CandidateExtractionModelAdapterTest {

    @Test
    void usesPortableFormatInstructionsForOpenAiCompatibleModel() {
        OpenAiChatModel model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
            .baseUrl("https://example.invalid/v1").apiKey("test-key").model("test-model").build()).build();
        assertPortablePrompt(model);
    }

    @Test
    void usesPortableFormatInstructionsForGenericModel() {
        assertPortablePrompt(mock(ChatModel.class));
    }

    @Test
    void convertsValidNormalAssistantJson() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        AiObservationService observations = TestAiObservationService.noop();
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(response("""
            {"nodes":[{"label":"Person","description":null,"confidence":0.9,"origin":"OBSERVED"}],
             "nodeProperties":[],"nodeKeys":[],"relationships":[],"relationshipProperties":[],"aliasSuggestions":[]}
            """));
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(resolver, observations);

        CandidateExtractionResult result = adapter.extract("portable format instructions");

        assertThat(result.nodes()).extracting(CandidateExtractionResult.NodeCandidate::label).containsExactly("Person");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).isEqualTo("portable format instructions");
    }

    @Test
    void rejectsMissingResponseResult() {
        CandidateExtractionModelAdapter adapter = adapterReturning(new ChatResponse(List.of()));

        assertThatThrownBy(() -> adapter.extract("portable"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Candidate model response is missing a result");
    }

    @Test
    void rejectsMissingAssistantMessage() {
        CandidateExtractionModelAdapter adapter = adapterReturning(new ChatResponse(List.of(new Generation(null))));

        assertThatThrownBy(() -> adapter.extract("portable"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Candidate model response is missing an assistant message");
    }

    @Test
    void rejectsBlankNormalContentEvenWhenReasoningMetadataContainsValidJson() {
        AssistantMessage message = AssistantMessage.builder().content("  ").properties(Map.of(
            "reasoning_content", validResponseJson())).build();
        CandidateExtractionModelAdapter adapter = adapterReturning(new ChatResponse(List.of(new Generation(message))));

        assertThatThrownBy(() -> adapter.extract("portable"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Candidate model response has blank normal assistant content");
    }

    @Test
    void rejectsMalformedNormalContentWithoutUsingReasoningMetadata() {
        AssistantMessage message = AssistantMessage.builder().content("not-json").properties(Map.of(
            "reasoning_content", validResponseJson())).build();
        CandidateExtractionModelAdapter adapter = adapterReturning(new ChatResponse(List.of(new Generation(message))));

        assertThatThrownBy(() -> adapter.extract("portable")).isInstanceOf(RuntimeException.class);
    }

    private void assertPortablePrompt(ChatModel model) {
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(
            mock(ProfileScopedAiClientResolver.class), TestAiObservationService.noop());
        String format = new BeanOutputConverter<>(CandidateExtractionResult.class).getFormat();
        String portablePrompt = "candidate instructions\n" + format;

        Prompt prompt = adapter.portablePrompt(model, portablePrompt);

        assertThat(prompt.getContents()).isEqualTo(portablePrompt).contains(format);
        assertThat(prompt.getOptions()).isNull();
    }

    private CandidateExtractionModelAdapter adapterReturning(ChatResponse response) {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(response);
        return new CandidateExtractionModelAdapter(resolver, TestAiObservationService.noop());
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private String validResponseJson() {
        return """
            {"nodes":[],"nodeProperties":[],"nodeKeys":[],"relationships":[],
             "relationshipProperties":[],"aliasSuggestions":[]}
            """;
    }

}
