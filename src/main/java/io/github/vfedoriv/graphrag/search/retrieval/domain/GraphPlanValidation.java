package io.github.vfedoriv.graphrag.search.retrieval.domain;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import java.time.Duration;
import java.util.List;
public final class GraphPlanValidation {
    private GraphPlanValidation() { }
    public record ValidatedGraphPlan(
        GraphPlan plan,
        SchemaSnapshot schemaContext,
        int maxRows,
        Duration timeout
    ) { }

    public record ValidationResult(boolean valid, List<String> errors, ValidatedGraphPlan validatedPlan) {
        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}
