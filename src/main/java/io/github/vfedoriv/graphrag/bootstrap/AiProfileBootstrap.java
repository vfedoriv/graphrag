package io.github.vfedoriv.graphrag.bootstrap;

import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Startup orchestration; profile construction and race handling remain AI-owned. */
@Component
public class AiProfileBootstrap implements ApplicationRunner {
    private final AiProfileService profiles;

    public AiProfileBootstrap(AiProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        profiles.seedDefaultProfile();
    }
}
