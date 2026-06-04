package io.github.vfedoriv.graphrag.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class AiObservationConfiguration {

    private static final String SPRING_AI_CLIENT_OBSERVATION = "gen_ai.client.operation";

    @Bean
    @Profile("langfuse")
    ObservationPredicate suppressSpringAiClientObservationsForLangfuse() {
        return (name, context) -> !SPRING_AI_CLIENT_OBSERVATION.equals(name);
    }
}
