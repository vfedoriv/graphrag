package io.github.vfedoriv.graphrag.observability;

import io.micrometer.observation.Observation;
import io.opentelemetry.api.trace.Span;

public final class AiModelCallObservation extends AiObservationScope {

    private final AiObservationService service;
    private final AiModelCallContext context;
    private final Observation parentObservation;
    private final Span parentSpan;
    private final long startNanos;
    private boolean recorded;

    AiModelCallObservation(
        AiObservationService service,
        AiModelCallContext context,
        Observation observation,
        Observation.Scope scope,
        Observation parentObservation,
        Span parentSpan
    ) {
        super(observation, scope);
        this.service = service;
        this.context = context;
        this.parentObservation = parentObservation;
        this.parentSpan = parentSpan;
        this.startNanos = System.nanoTime();
    }

    static AiModelCallObservation noop(AiObservationService service, AiModelCallContext context) {
        Observation observation = Observation.NOOP;
        return new AiModelCallObservation(service, context, observation, observation.openScope(), Observation.NOOP, Span.getInvalid());
    }

    @Override
    public void highCardinalityAttribute(String key, String value) {
        super.highCardinalityAttribute(key, value);
        if (AiObservationService.isInputOutputAttribute(key) && value != null) {
            service.addTraceInputOutputAttributes(parentObservation, java.util.Map.of(key, value));
            service.addSpanInputOutputAttributes(parentSpan, java.util.Map.of(key, value));
            service.addSpanInputOutputAttributes(Span.current(), java.util.Map.of(key, value));
        }
    }

    public void success(AiTokenUsage tokenUsage) {
        if (recorded) {
            return;
        }
        recorded = true;
        super.success();
        service.recordModelCall(context, AiObservationAttributes.STATUS_SUCCESS, null, System.nanoTime() - startNanos, tokenUsage);
    }

    @Override
    public void error(Throwable throwable) {
        if (!recorded) {
            recorded = true;
            service.recordModelCall(
                context,
                AiObservationAttributes.STATUS_FAILURE,
                AiObservationService.failureCategory(throwable),
                System.nanoTime() - startNanos,
                AiTokenUsage.none()
            );
        }
        super.error(throwable);
    }
}
