package io.github.vfedoriv.graphrag.schemas.evaluation.application;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftAdmissions;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationRunSummaryResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.contracts.EvaluationNavigationFacts;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
@Service
public class EvaluationHistoryService implements EvaluationNavigationFacts {
    private final SchemaDraftEvaluationRunRepository runs;
    public EvaluationHistoryService(SchemaDraftEvaluationRunRepository runs) { this.runs = runs; }
    @RelationalTransactional(readOnly = true)
    public EvaluationRunPageResponse page(DraftAdmissions.Draft draft, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<SchemaDraftEvaluationRunNode> result = runs.findPageByDraftId(draft.id(), PageRequest.of(boundedPage, boundedSize));
        return new EvaluationRunPageResponse(boundedPage, boundedSize, result.getTotalElements(),
            result.getContent().stream().map(run -> summary(draft, run)).toList());
    }
    @Override @RelationalTransactional(readOnly = true)
    public Map<String, Reference> latest(List<Context> contexts) {
        if (contexts.isEmpty()) return Map.of();
        Map<String, Context> byId = contexts.stream().collect(Collectors.toMap(Context::draftId, Function.identity()));
        Map<String, Reference> result = new LinkedHashMap<>();
        for (SchemaDraftEvaluationRunNode run : runs.findLatestForDraftIds(contexts.stream().map(Context::draftId).toList())) {
            Context context = byId.get(run.getDraftId());
            if (context != null) result.put(run.getDraftId(), new Reference(run.getId(), run.getStatus().name(),
                current(context.revision(), context.aggregateRevisionId(), run), location(run)));
        }
        return Map.copyOf(result);
    }
    public boolean isCurrent(DraftAdmissions.Draft draft, SchemaDraftEvaluationRunNode run) {
        return draft != null && current(draft.revision(), draft.aggregateRevisionId(), run);
    }
    private boolean current(long revision, String aggregateRevisionId, SchemaDraftEvaluationRunNode run) {
        return run.getDraftRevision() == revision && run.getAggregateRevisionId() != null
            && run.getAggregateRevisionId().equals(aggregateRevisionId) && run.getProjectionContentHash() != null;
    }
    private EvaluationRunSummaryResponse summary(DraftAdmissions.Draft draft, SchemaDraftEvaluationRunNode run) {
        boolean terminal = run.getStatus() != SchemaDraftEvaluationStatus.QUEUED && run.getStatus() != SchemaDraftEvaluationStatus.RUNNING;
        return new EvaluationRunSummaryResponse(run.getId(), run.getStatus(), run.getDraftRevision(), run.getAggregateRevisionId(),
            run.getProjectionContentHash(), run.getAiProfileId(), run.getAiProfileRevision(), run.getPromptRevision(), run.getContractRevision(),
            run.getRetryOfRunId(), run.getTotalDocuments(), run.getSucceededDocuments(), run.getFailedDocuments(), run.getStaleDocuments(),
            isCurrent(draft, run), terminal && draft.open() && run.getDraftRevision() == draft.revision(), run.getFailureCategory(),
            run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt(), location(run));
    }
    private String location(SchemaDraftEvaluationRunNode run) {
        return "/api/v1/knowledge-bases/" + run.getKnowledgeBaseId() + "/schema-drafts/" + run.getDraftId() + "/evaluation-runs/" + run.getId();
    }
}
