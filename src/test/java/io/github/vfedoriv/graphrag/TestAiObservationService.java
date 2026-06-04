package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

public final class TestAiObservationService {

    private TestAiObservationService() {
    }

    public static AiObservationService noop() {
        return new AiObservationService(
            AiObservabilityProperties.disabled(),
            ObservationRegistry.NOOP,
            new SimpleMeterRegistry(),
            null,
            null
        );
    }
}
