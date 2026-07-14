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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

class CandidateExtractionModelAdapterTest {

    @Test
    void selectsProviderNativeOutputOnlyForOpenAiModel() {
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(mock(ProfileScopedAiClientResolver.class),
            TestAiObservationService.noop());

        OpenAiChatModel openAiModel = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
            .baseUrl("https://example.invalid/v1").apiKey("test-key").model("test-model").build()).build();
        assertThat(adapter.supportsProviderNative(openAiModel)).isTrue();
        assertThat(adapter.supportsProviderNative(mock(ChatModel.class))).isFalse();
    }

    @Test
    void convertsPortableTopLevelCandidateContainer() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        AiObservationService observations = TestAiObservationService.noop();
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(response("""
            {"nodes":[{"label":"Person","description":null,"confidence":0.9,"origin":"OBSERVED"}],
             "nodeProperties":[],"nodeKeys":[],"relationships":[],"relationshipProperties":[],"aliasSuggestions":[]}
            """));
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(resolver, observations);

        CandidateExtractionResult result = adapter.extract("native", "portable format instructions");

        assertThat(result.nodes()).extracting(CandidateExtractionResult.NodeCandidate::label).containsExactly("Person");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).isEqualTo("portable format instructions");
    }

    @Test
    void reportsConversionFailure() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        AiObservationService observations = TestAiObservationService.noop();
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(response("not-json"));
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(resolver, observations);

        assertThatThrownBy(() -> adapter.extract("native", "portable")).isInstanceOf(RuntimeException.class);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
