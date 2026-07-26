package io.github.vfedoriv.graphrag.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import com.openai.core.http.Headers;
import com.openai.errors.UnauthorizedException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
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
            .isInstanceOf(EmptyModelResponseException.class)
            .extracting("failureCode")
            .isEqualTo(SourceFailureCode.EMPTY_MODEL_RESPONSE);
    }

    @Test
    void rejectsMissingAssistantMessage() {
        CandidateExtractionModelAdapter adapter = adapterReturning(new ChatResponse(List.of(new Generation(null))));

        assertThatThrownBy(() -> adapter.extract("portable"))
            .isInstanceOf(EmptyModelResponseException.class);
    }

    @Test
    void rejectsBlankNormalContentEvenWhenReasoningMetadataContainsValidJson() {
        AssistantMessage message = AssistantMessage.builder().content("  ").properties(Map.of(
            "reasoning_content", validResponseJson())).build();
        CandidateExtractionModelAdapter adapter = adapterReturning(new ChatResponse(List.of(new Generation(message))));

        assertThatThrownBy(() -> adapter.extract("portable"))
            .isInstanceOf(EmptyModelResponseException.class);
    }

    @Test
    void rejectsMalformedNormalContentWithoutUsingReasoningMetadata() {
        AssistantMessage message = AssistantMessage.builder().content("not-json").properties(Map.of(
            "reasoning_content", validResponseJson())).build();
        CandidateExtractionModelAdapter adapter = adapterReturning(new ChatResponse(List.of(new Generation(message))));

        assertThatThrownBy(() -> adapter.extract("portable")).isInstanceOf(MalformedModelResponseException.class);
    }

    @Test
    void retriesInvalidOutputOnceAndReturnsOnlyValidatedAttempt() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(response("not-json"), response(validResponseJson()));
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(
            resolver, TestAiObservationService.noop());

        CandidateExtractionResult result = adapter.extract("portable");

        assertThat(result.nodes()).isEmpty();
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void doesNotAddApplicationRetryForTransportFailure() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenThrow(new com.openai.errors.OpenAIIoException("safe transport failure"));
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(
            resolver, TestAiObservationService.noop());

        assertThatThrownBy(() -> adapter.extract("portable"))
            .isInstanceOf(com.openai.errors.OpenAIIoException.class);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void doesNotAddApplicationRetryForPermanentProviderFailure() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        UnauthorizedException failure = UnauthorizedException.builder()
            .headers(Headers.builder().build()).error(Optional.empty()).build();
        when(model.call(any(Prompt.class))).thenThrow(failure);
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(
            resolver, TestAiObservationService.noop());

        assertThatThrownBy(() -> adapter.extract("portable")).isSameAs(failure);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void doesNotStartOutputRetryAfterSourceDeadline() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(100);
            return response("not-json");
        });
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(
            resolver, TestAiObservationService.noop());
        long now = System.nanoTime();
        CandidateExtractionAttemptContext context = CandidateExtractionAttemptContext.forSource("source")
            .withDeadlines(now + TimeUnit.MILLISECONDS.toNanos(50), now + TimeUnit.SECONDS.toNanos(1));

        assertThatThrownBy(() -> adapter.extractValidated("portable", context, java.util.function.Function.identity()))
            .isInstanceOf(DiscoveryDeadlineExceededException.class)
            .extracting("failureCode")
            .isEqualTo(SourceFailureCode.SOURCE_DEADLINE_EXCEEDED);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void warningsExcludePromptOutputReasoningAndRawProviderMessages(CapturedOutput output) {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenThrow(
            new com.openai.errors.OpenAIIoException("PRIVATE_PROVIDER_BODY_0ab81f api-key-secret"));
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(
            resolver, TestAiObservationService.noop());

        assertThatThrownBy(() -> adapter.extract("PRIVATE_PROMPT_37c61d"))
            .isInstanceOf(com.openai.errors.OpenAIIoException.class);

        assertThat(output.getAll())
            .contains("failureCode=TRANSPORT_IO", "messageFingerprint=sha256:")
            .doesNotContain("PRIVATE_PROVIDER_BODY_0ab81f", "api-key-secret", "PRIVATE_PROMPT_37c61d");
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
