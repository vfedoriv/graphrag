package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftEvaluationSummaries;
import io.github.vfedoriv.graphrag.schemas.evaluation.contracts.EvaluationNavigationFacts;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DraftEvaluationSummariesAdapter implements DraftEvaluationSummaries {
    private final EvaluationNavigationFacts facts;
    public DraftEvaluationSummariesAdapter(EvaluationNavigationFacts facts) { this.facts = facts; }
    @Override public Map<String, Reference> latest(List<Context> drafts) {
        List<EvaluationNavigationFacts.Context> contexts = drafts.stream()
            .map(value -> new EvaluationNavigationFacts.Context(value.draftId(), value.revision(), value.aggregateRevisionId())).toList();
        return Map.copyOf(facts.latest(contexts).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            entry -> new Reference(entry.getValue().id(), entry.getValue().status(), entry.getValue().current(), entry.getValue().statusLocation()))));
    }
}
