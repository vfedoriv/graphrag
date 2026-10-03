package io.github.vfedoriv.graphrag.search.answering.domain;


import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchPlanningContracts.Refinement;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchPlanningContracts.SufficiencyResult;
import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchFollowUpPolicy {

    public Decision decide(
        SufficiencyResult sufficiency,
        Instant deadline,
        AdvancedSearchSettings settings,
        BooleanSupplier cancelled
    ) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            return new Decision(false, List.of(), "CANCELLED");
        }
        if (sufficiency == null || sufficiency.gap() == null || !sufficiency.gap().concrete()) {
            return new Decision(false, List.of(), "NO_CONCRETE_GAP");
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        Duration required = settings.followUpMinimumRemaining().plus(settings.synthesisReserve());
        if (remaining.compareTo(required) < 0) {
            return new Decision(false, List.of(), "DEADLINE_RESERVE");
        }
        List<Refinement> refinements = sufficiency.refinements().stream()
            .limit(settings.followUpMaxQueries())
            .toList();
        if (refinements.isEmpty()) {
            return new Decision(false, List.of(), "NO_REFINEMENTS");
        }
        return new Decision(true, refinements, null);
    }

    public record Decision(boolean execute, List<Refinement> refinements, String skippedCategory) {
        public Decision {
            refinements = refinements == null ? List.of() : List.copyOf(refinements);
        }
    }
}
