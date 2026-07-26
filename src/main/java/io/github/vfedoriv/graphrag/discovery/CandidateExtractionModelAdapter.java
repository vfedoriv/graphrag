package io.github.vfedoriv.graphrag.discovery;

import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CandidateExtractionModelAdapter {

    private final ProfileScopedAiClientResolver clientResolver;
    private final AiObservationService observationService;
    private final SourceFailureClassifier failureClassifier;

    @Autowired
    public CandidateExtractionModelAdapter(
        ProfileScopedAiClientResolver clientResolver, AiObservationService observationService,
        SourceFailureClassifier failureClassifier
    ) {
        this.clientResolver = clientResolver;
        this.observationService = observationService;
        this.failureClassifier = failureClassifier;
    }

    public CandidateExtractionModelAdapter(
        ProfileScopedAiClientResolver clientResolver, AiObservationService observationService
    ) {
        this(clientResolver, observationService, new SourceFailureClassifier());
    }

    public CandidateExtractionResult extract(String portablePrompt) {
        return extractValidated(portablePrompt, CandidateExtractionAttemptContext.forSource(null), Function.identity());
    }

    public <T> T extractValidated(
        String portablePrompt,
        CandidateExtractionAttemptContext context,
        Function<CandidateExtractionResult, T> validator
    ) {
        ChatModel model = requireModel();
        BeanOutputConverter<CandidateExtractionResult> converter = new BeanOutputConverter<>(CandidateExtractionResult.class);
        Prompt prompt = portablePrompt(model, portablePrompt);
        for (int attempt = 1; attempt <= 2; attempt++) {
            CandidateExtractionAttemptContext attemptContext = context.forOutputAttempt(attempt);
            long startNanos = System.nanoTime();
            try {
                return outputAttempt(model, prompt, converter, validator, attemptContext);
            } catch (ModelOutputException exception) {
                SourceFailureDecision decision = failureClassifier.classify(exception);
                if (attempt == 1) {
                    log.warn("Candidate model output will be retried: draftId={}, runId={}, sourceId={}, sourceRevision={}, chunkId={}, outputAttempt={}, elapsedMs={}, failureCategory={}, failureCode={}, responseLength={}, responseFingerprint={}, finishReason={}, inputTokens={}, outputTokens={}, totalTokens={}, reasoningPresent={}, reasoningLength={}",
                        attemptContext.draftId(), attemptContext.runId(), attemptContext.sourceId(),
                        attemptContext.sourceRevision(), attemptContext.chunkId(), attempt,
                        LogMetadata.elapsedMillis(startNanos), decision.category(), decision.code(),
                        exception.diagnostics().normalContentLength(),
                        exception.diagnostics().normalContentFingerprint(),
                        exception.diagnostics().finishReason(),
                        exception.diagnostics().tokenUsage().inputTokens(),
                        exception.diagnostics().tokenUsage().outputTokens(),
                        exception.diagnostics().tokenUsage().totalTokens(),
                        exception.diagnostics().reasoningContentPresent(),
                        exception.diagnostics().reasoningContentLength());
                    continue;
                }
                logTerminalFailure(attemptContext, startNanos, decision, exception.diagnostics());
                throw exception;
            }
        }
        throw new IllegalStateException("Candidate output retry loop exhausted unexpectedly");
    }

    private <T> T outputAttempt(
        ChatModel model,
        Prompt prompt,
        BeanOutputConverter<CandidateExtractionResult> converter,
        Function<CandidateExtractionResult, T> validator,
        CandidateExtractionAttemptContext context
    ) {
        long startNanos = System.nanoTime();
        Map<String, String> attributes = new HashMap<>(observationService.contentAttributes("ai.prompt", prompt.getContents()));
        attributes.put("ai.structured_output.mode", "portable");
        attributes.putAll(context.observationAttributes());
        attributes.putAll(observationService.langfuseInputAttributes(prompt.getContents()));
        try (AiModelCallObservation observation = observationService.startChatModelCall(
            AiObservationService.WORKFLOW_SCHEMA_DISCOVERY, null, attributes
        )) {
            ChatResponse response = null;
            ModelResponseDiagnostics diagnostics = ModelResponseDiagnostics.none();
            try {
                response = model.call(prompt);
                diagnostics = ModelResponseDiagnostics.from(response);
                observation.highCardinalityAttributes(diagnostics.observationAttributes());
                String responseText = responseText(response, diagnostics);
                observation.highCardinalityAttributes(observationService.langfuseOutputAttributes(responseText));
                CandidateExtractionResult result;
                try {
                    result = converter.convert(responseText);
                } catch (RuntimeException exception) {
                    throw new MalformedModelResponseException(diagnostics);
                }
                if (result == null) {
                    throw new MalformedModelResponseException(diagnostics);
                }
                T validated;
                try {
                    validated = validator.apply(result);
                } catch (ModelOutputException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new InvalidModelCandidateException(diagnostics);
                }
                observation.success(AiTokenUsage.fromResponse(response));
                return validated;
            } catch (RuntimeException exception) {
                SourceFailureDecision decision = failureClassifier.classify(exception);
                observation.highCardinalityAttribute("ai.failure.category", decision.category().name());
                observation.highCardinalityAttribute("ai.failure.code", decision.code().name());
                observation.highCardinalityAttribute("ai.failure.retryable", Boolean.toString(decision.retryable()));
                if (decision.providerStatus() != null) {
                    observation.highCardinalityAttribute("ai.failure.provider_status",
                        Integer.toString(decision.providerStatus()));
                }
                observation.highCardinalityAttribute("ai.failure.exception_types",
                    String.join(",", decision.exceptionTypes()));
                observation.highCardinalityAttribute("ai.failure.root_exception_type", decision.rootExceptionType());
                observation.highCardinalityAttribute("ai.failure.message_fingerprint", decision.messageFingerprint());
                observation.error(exception, decision.category().name().toLowerCase(java.util.Locale.ROOT));
                if (!(exception instanceof ModelOutputException)) {
                    logTerminalFailure(context, startNanos, decision, ModelResponseDiagnostics.none());
                }
                throw exception;
            }
        }
    }

    private ChatModel requireModel() {
        ChatModel model = clientResolver.chatModel();
        if (model == null) {
            throw new IllegalStateException("No Spring AI ChatModel bean is configured");
        }
        return model;
    }

    Prompt portablePrompt(ChatModel model, String content) {
        return new Prompt(content);
    }

    private String responseText(ChatResponse response, ModelResponseDiagnostics diagnostics) {
        if (response == null || response.getResult() == null) {
            throw new EmptyModelResponseException(diagnostics);
        }
        AssistantMessage message = response.getResult().getOutput();
        if (message == null) {
            throw new EmptyModelResponseException(diagnostics);
        }
        String content = message.getText() == null ? "" : message.getText().trim();
        if (content.isBlank()) {
            throw new EmptyModelResponseException(diagnostics);
        }
        return content;
    }

    private void logTerminalFailure(
        CandidateExtractionAttemptContext context,
        long startNanos,
        SourceFailureDecision decision,
        ModelResponseDiagnostics diagnostics
    ) {
        long elapsedMillis = startNanos == 0 ? 0 : LogMetadata.elapsedMillis(startNanos);
        log.warn("Candidate model call failed: draftId={}, runId={}, sourceId={}, sourceRevision={}, chunkId={}, outputAttempt={}, elapsedMs={}, failureCategory={}, failureCode={}, retryable={}, providerStatus={}, configuredTimeoutSeconds={}, configuredSdkMaxRetries={}, responseId={}, responseModel={}, finishReason={}, responseLength={}, responseFingerprint={}, inputTokens={}, outputTokens={}, totalTokens={}, reasoningPresent={}, reasoningLength={}, exceptionTypes={}, rootExceptionType={}, messageFingerprint={}",
            context.draftId(), context.runId(), context.sourceId(), context.sourceRevision(), context.chunkId(),
            context.outputAttempt(), elapsedMillis, decision.category(), decision.code(), decision.retryable(),
            decision.providerStatus(), context.configuredTimeoutSeconds(), context.configuredSdkMaxRetries(),
            diagnostics.responseId(), diagnostics.model(), diagnostics.finishReason(),
            diagnostics.normalContentLength(), diagnostics.normalContentFingerprint(),
            diagnostics.tokenUsage().inputTokens(), diagnostics.tokenUsage().outputTokens(),
            diagnostics.tokenUsage().totalTokens(), diagnostics.reasoningContentPresent(),
            diagnostics.reasoningContentLength(),
            decision.exceptionTypes(), decision.rootExceptionType(), decision.messageFingerprint());
    }
}
