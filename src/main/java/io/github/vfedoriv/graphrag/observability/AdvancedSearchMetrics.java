package io.github.vfedoriv.graphrag.observability;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.search.runs.ports.AdvancedSearchRunProcessor.Attempt;
import io.github.vfedoriv.graphrag.search.answering.domain.AnswerSynthesis.Outcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdvancedSearchMetrics {

    private final MeterRegistry registry;

    public AdvancedSearchMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void retrieval(List<Attempt> attempts) {
        for (Attempt attempt : attempts) {
            Counter.builder("graphrag.advanced_search.retrieval.branches")
                .tag("retriever", stable(attempt.retriever()))
                .tag("status", stable(attempt.status()))
                .tag("failure", stable(attempt.failureCategory()))
                .register(registry).increment();
            Timer.builder("graphrag.advanced_search.retrieval.latency")
                .tag("retriever", stable(attempt.retriever()))
                .register(registry).record(Duration.ofMillis(Math.max(0, attempt.latencyMs())));
        }
    }

    public void answer(Outcome outcome, boolean followUpExecuted, int evidenceCount) {
        Counter.builder("graphrag.advanced_search.answers")
            .tag("status", outcome.answer().status().name())
            .tag("abstained", String.valueOf(!outcome.answered()))
            .tag("repair", outcome.diagnostics().repairAttempted()
                ? outcome.diagnostics().repairSucceeded() ? "succeeded" : "failed" : "unused")
            .tag("follow_up", String.valueOf(followUpExecuted))
            .register(registry).increment();
        registry.summary("graphrag.advanced_search.evidence.count").record(evidenceCount);
        registry.summary("graphrag.advanced_search.citations.count")
            .record(outcome.diagnostics().citationCount());
    }

    public void terminal(AdvancedSearchRunStatus status, String category) {
        Counter.builder("graphrag.advanced_search.runs")
            .tag("status", status.name())
            .tag("category", stable(category))
            .register(registry).increment();
    }

    private String stable(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(80, sanitized.length()));
    }
}
