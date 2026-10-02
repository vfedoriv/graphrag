package io.github.vfedoriv.graphrag.search.answering.domain;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.Answer;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.DiagnosticResult;
/** Immutable synthesis outcome consumed by orchestration and shared metrics. */
public final class AnswerSynthesis {
    private AnswerSynthesis() { }
    public record Outcome(Answer answer, DiagnosticResult diagnostics, boolean answered) { }
}
