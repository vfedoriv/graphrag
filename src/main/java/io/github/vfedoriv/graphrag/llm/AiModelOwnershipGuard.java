package io.github.vfedoriv.graphrag.llm;

import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AiModelOwnershipGuard implements ApplicationRunner {

    private final Environment environment;
    private final Map<String, ChatModel> chatModels;
    private final Map<String, EmbeddingModel> embeddingModels;

    public AiModelOwnershipGuard(
        Environment environment,
        Map<String, ChatModel> chatModels,
        Map<String, EmbeddingModel> embeddingModels
    ) {
        this.environment = environment;
        this.chatModels = chatModels;
        this.embeddingModels = embeddingModels;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isAiProfileActive()) {
            requireSingleBean("Spring AI ChatModel", chatModels);
            requireSingleBean("Spring AI EmbeddingModel", embeddingModels);
        }
    }

    private boolean isAiProfileActive() {
        for (String profile : environment.getActiveProfiles()) {
            if ("openai".equals(profile) || "lm_studio".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private void requireSingleBean(String typeName, Map<String, ?> beans) {
        if (beans.size() != 1) {
            throw new IllegalStateException(
                typeName + " ownership must resolve to exactly one Spring AI bean for AI profiles, found " +
                    beans.size() + ": " + beans.keySet()
            );
        }
    }
}
