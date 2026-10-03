package io.github.vfedoriv.graphrag.schemas.evaluation.application;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftAdmissions;
import io.github.vfedoriv.graphrag.schemas.evaluation.contracts.EvaluationNavigationFacts;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
class EvaluationHistoryServiceTest {
    private final SchemaDraftEvaluationRunRepository runs = mock(SchemaDraftEvaluationRunRepository.class);
    private final EvaluationHistoryService history = new EvaluationHistoryService(runs);
    @Test void currentnessUsesRevisionAggregateAndNonNullProjectionHash() {
        SchemaDraftEvaluationRunNode current = run("current", 2, "aggregate");
        SchemaDraftEvaluationRunNode stale = run("stale", 1, "aggregate");
        assertThat(history.isCurrent(draft(true), current)).isTrue();
        assertThat(history.isCurrent(draft(true), stale)).isFalse();
        current.setAggregateRevisionId(null);
        assertThat(history.isCurrent(draft(true), current)).isFalse();
        current.setAggregateRevisionId("aggregate"); current.setProjectionContentHash(null);
        assertThat(history.isCurrent(draft(true), current)).isFalse();
    }
    @Test void historyRetainsPaginationTotalAndRetryEligibility() {
        SchemaDraftEvaluationRunNode stale = run("stale", 1, "old");
        SchemaDraftEvaluationRunNode current = run("current", 2, "aggregate");
        when(runs.findPageByDraftId("draft", PageRequest.of(0, 100)))
            .thenReturn(new PageImpl<>(List.of(current, stale), PageRequest.of(0, 100), 123));
        io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationRunPageResponse result = history.page(draft(true), -1, 200);
        assertThat(result.getTotalElements()).isEqualTo(123);
        assertThat(result.getContent()).extracting(value -> value.current()).containsExactly(true, false);
        assertThat(result.getContent()).extracting(value -> value.retryable()).containsExactly(true, false);
        assertThat(history.page(draft(false), 0, 100).getContent().getFirst().retryable()).isFalse();
    }
    @Test void latestUsesOneBatchReadAndNoReadForEmptyInput() {
        assertThat(history.latest(List.of())).isEmpty();
        verifyNoInteractions(runs);
        SchemaDraftEvaluationRunNode run = run("run", 2, "aggregate");
        when(runs.findLatestForDraftIds(List.of("draft", "other"))).thenReturn(List.of(run));
        Map<String, EvaluationNavigationFacts.Reference> result = history.latest(List.of(
            new EvaluationNavigationFacts.Context("draft", 2, "aggregate"), new EvaluationNavigationFacts.Context("other", 3, "other-aggregate")));
        assertThat(result).containsOnlyKeys("draft");
        assertThat(result.get("draft").current()).isTrue();
        assertThat(result.get("draft").statusLocation()).isEqualTo("/api/v1/knowledge-bases/kb/schema-drafts/draft/evaluation-runs/run");
        verify(runs).findLatestForDraftIds(List.of("draft", "other"));
        verifyNoMoreInteractions(runs);
    }
    private DraftAdmissions.Draft draft(boolean open) { return new DraftAdmissions.Draft("draft", "kb", 2, null, "target", 1, "aggregate", "{}", open, null, null); }
    private SchemaDraftEvaluationRunNode run(String id, long revision, String aggregate) {
        SchemaDraftEvaluationRunNode run = new SchemaDraftEvaluationRunNode(); run.setId(id); run.setDraftId("draft"); run.setKnowledgeBaseId("kb");
        run.setDraftRevision(revision); run.setAggregateRevisionId(aggregate); run.setProjectionContentHash("hash"); run.setStatus(SchemaDraftEvaluationStatus.COMPLETED); return run;
    }
}
