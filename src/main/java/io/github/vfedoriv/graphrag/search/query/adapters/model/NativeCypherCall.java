package io.github.vfedoriv.graphrag.search.query.adapters.model;

import com.openai.errors.OpenAIServiceException;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

/** Native protocol checks, deliberately private to this workflow adapter. */
final class NativeCypherCall {
    private static final Set<String> OUTPUT_FAILURES = Set.of("MODEL_REFUSAL", "INCOMPLETE_MODEL_OUTPUT",
        "EMPTY_MODEL_OUTPUT", "INVALID_NATIVE_OUTPUT", "NATIVE_FORMAT_UNAVAILABLE", "NATIVE_FORMAT_REJECTED");
    static Prompt prompt(ChatModel model, String text) {
        if (!(model instanceof OpenAiChatModel openAi)) throw new IllegalArgumentException("NATIVE_FORMAT_UNAVAILABLE");
        return new Prompt(text, openAi.getOptions().mutate().responseFormat(OpenAiChatModel.ResponseFormat.builder()
            .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA).jsonSchema(NativeCypherOutput.SCHEMA)
            .strict(true).build()).build());
    }
    static String content(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null)
            throw new IllegalArgumentException("EMPTY_MODEL_OUTPUT");
        Generation generation = response.getResult();
        Object refusal = generation.getOutput().getMetadata().get("refusal");
        if (refusal != null && (!(refusal instanceof String text) || !text.isBlank()))
            throw new IllegalArgumentException("MODEL_REFUSAL");
        String finish = generation.getMetadata().getFinishReason();
        if (!"stop".equalsIgnoreCase(finish)) throw new IllegalArgumentException("INCOMPLETE_MODEL_OUTPUT");
        String content = generation.getOutput().getText();
        if (content == null || content.isBlank()) throw new IllegalArgumentException("EMPTY_MODEL_OUTPUT");
        return content;
    }
    static String category(Exception exception) {
        if (exception instanceof IllegalArgumentException && OUTPUT_FAILURES.contains(exception.getMessage()))
            return exception.getMessage();
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof OpenAIServiceException provider && provider.statusCode() == 400) {
                String code = provider.code().orElse("");
                String param = provider.param().orElse("");
                if (Set.of("unsupported_response_format", "invalid_json_schema", "unsupported_json_schema").contains(code)
                    || (Set.of("unsupported_value", "unsupported_parameter", "invalid_value").contains(code)
                        && (param.equals("response_format") || param.startsWith("response_format."))))
                    return "NATIVE_FORMAT_REJECTED";
            }
        }
        return "PROVIDER_FAILURE";
    }
}
