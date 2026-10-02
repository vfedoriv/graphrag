package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftReprocessingSummaries;
import io.github.vfedoriv.graphrag.schemas.reprocessing.contracts.ReprocessingNavigationFacts;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DraftReprocessingSummariesAdapter implements DraftReprocessingSummaries {
    private final ReprocessingNavigationFacts facts;
    public DraftReprocessingSummariesAdapter(ReprocessingNavigationFacts facts) { this.facts = facts; }
    @Override public Map<String, Reference> latest(List<String> draftIds) {
        return Map.copyOf(facts.latest(draftIds).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            entry -> new Reference(entry.getValue().id(), entry.getValue().status(), entry.getValue().current(), entry.getValue().statusLocation()))));
    }
}
