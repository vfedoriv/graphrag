package io.github.vfedoriv.graphrag.observability;

import io.micrometer.observation.Observation;
import java.util.Map;

public class AiObservationScope implements AutoCloseable {

    private final Observation observation;
    private final Observation.Scope scope;

    AiObservationScope(Observation observation, Observation.Scope scope) {
        this.observation = observation;
        this.scope = scope;
    }

    static AiObservationScope noop() {
        Observation observation = Observation.NOOP;
        return new AiObservationScope(observation, observation.openScope());
    }

    public void success() {
        observation.lowCardinalityKeyValue(AiObservationAttributes.STATUS, AiObservationAttributes.STATUS_SUCCESS);
    }

    public void error(Throwable throwable) {
        observation.lowCardinalityKeyValue(AiObservationAttributes.STATUS, AiObservationAttributes.STATUS_FAILURE);
        observation.lowCardinalityKeyValue(AiObservationAttributes.FAILURE_CATEGORY, AiObservationService.failureCategory(throwable));
        observation.error(throwable);
    }

    public void lowCardinalityAttribute(String key, String value) {
        if (key != null && value != null) observation.lowCardinalityKeyValue(key, value);
    }

    public void highCardinalityAttribute(String key, String value) {
        if (key != null && value != null) {
            observation.highCardinalityKeyValue(key, value);
        }
    }

    public void highCardinalityAttributes(Map<String, String> attributes) {
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            highCardinalityAttribute(attribute.getKey(), attribute.getValue());
        }
    }

    @Override
    public void close() {
        scope.close();
        observation.stop();
    }
}
