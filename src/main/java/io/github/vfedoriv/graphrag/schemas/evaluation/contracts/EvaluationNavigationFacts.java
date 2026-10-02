package io.github.vfedoriv.graphrag.schemas.evaluation.contracts;
import java.util.List;
import java.util.Map;
/** Bounded immutable navigation facts produced by evaluation ownership. */
public interface EvaluationNavigationFacts {
    Map<String, Reference> latest(List<Context> contexts);
    record Context(String draftId, long revision, String aggregateRevisionId) { }
    record Reference(String id, String status, boolean current, String statusLocation) { }
}
