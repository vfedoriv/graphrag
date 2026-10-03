package io.github.vfedoriv.graphrag.schemas.drafts.application;



import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisRunSummaryResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisWorkflowReference;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.EvaluationWorkflowReference;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.ReprocessingWorkflowReference;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftEvaluationSummaries;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftReprocessingSummaries;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
public class SchemaDraftWorkflowNavigationService {
    private final SchemaDraftAnalysisRunRepository analysisRepository;
    private final DraftEvaluationSummaries evaluationSummaries;
    private final DraftReprocessingSummaries reprocessingSummaries;
    private final SchemaDraftSourceRepository sourceRepository;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService;

    public SchemaDraftWorkflowNavigationService(
        SchemaDraftAnalysisRunRepository analysisRepository,
        DraftEvaluationSummaries evaluationSummaries,
        DraftReprocessingSummaries reprocessingSummaries,
        SchemaDraftSourceRepository sourceRepository,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService
    ) {
        this.analysisRepository = analysisRepository;
        this.evaluationSummaries = evaluationSummaries;
        this.reprocessingSummaries = reprocessingSummaries;
        this.sourceRepository = sourceRepository;
        this.jsonSupport = jsonSupport;
        this.analysisRetryEligibilityService = analysisRetryEligibilityService;
    }

    @RelationalTransactional(readOnly = true)
    public AnalysisRunPageResponse analysisPage(SchemaDraftNode draft, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<SchemaDraftAnalysisRunNode> runs = analysisRepository.findPageByDraftId(
            draft.getId(), PageRequest.of(boundedPage, boundedSize));
        SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs retryInputs =
            analysisRetryEligibilityService.inputs(draft);
        List<AnalysisRunSummaryResponse> content = runs.getContent().stream()
            .map(run -> analysisSummary(draft, run, retryInputs)).toList();
        return new AnalysisRunPageResponse(boundedPage, boundedSize, runs.getTotalElements(), content);
    }

    @RelationalTransactional(readOnly = true)
    public Map<String, WorkflowReferences> references(List<SchemaDraftNode> drafts) {
        if (drafts.isEmpty()) {
            return Map.of();
        }
        List<String> draftIds = drafts.stream().map(SchemaDraftNode::getId).toList();
        Map<String, SchemaDraftNode> draftsById = drafts.stream()
            .collect(Collectors.toMap(SchemaDraftNode::getId, Function.identity()));
        Map<String, String> memberships = sourceRepository.findActiveForDraftIds(draftIds).stream()
            .collect(Collectors.groupingBy(SchemaDraftSourceNode::getDraftId))
            .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> membershipFingerprint(entry.getValue())));
        Map<String, SchemaDraftAnalysisRunNode> analyses = analysisRepository.findCurrentForDraftIds(draftIds).stream()
            .filter(run -> isAnalysisCurrent(
                draftsById.get(run.getDraftId()), run,
                memberships.getOrDefault(run.getDraftId(), jsonSupport.fingerprint(""))))
            .collect(Collectors.toMap(SchemaDraftAnalysisRunNode::getDraftId, Function.identity(), this::newerAnalysis));
        Map<String, DraftEvaluationSummaries.Reference> evaluations = evaluationSummaries.latest(drafts.stream()
            .map(draft -> new DraftEvaluationSummaries.Context(draft.getId(), draft.getRevision(), draft.getCurrentAggregateId())).toList());
        Map<String, DraftReprocessingSummaries.Reference> plans = reprocessingSummaries.latest(draftIds);
        Map<String, WorkflowReferences> result = new HashMap<>();
        for (SchemaDraftNode draft : drafts) {
            SchemaDraftAnalysisRunNode analysis = analyses.get(draft.getId());
            DraftEvaluationSummaries.Reference evaluation = evaluations.get(draft.getId());
            DraftReprocessingSummaries.Reference plan = plans.get(draft.getId());
            result.put(draft.getId(), new WorkflowReferences(
                analysis == null ? null : new AnalysisWorkflowReference(
                    analysis.getId(), analysis.getStatus(), true, analysisLocation(analysis)),
                evaluation == null ? null : new EvaluationWorkflowReference(
                    evaluation.id(), io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus.valueOf(evaluation.status()), evaluation.current(), true,
                    evaluation.statusLocation()),
                plan == null ? null : new ReprocessingWorkflowReference(
                    plan.id(), io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus.valueOf(plan.status()), plan.current(), true,
                    plan.statusLocation())));
        }
        return Map.copyOf(result);
    }

    private AnalysisRunSummaryResponse analysisSummary(
        SchemaDraftNode draft, SchemaDraftAnalysisRunNode run,
        SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs retryInputs
    ) {
        SchemaDraftAnalysisRetryEligibilityService.EligibilityDecision retryDecision =
            analysisRetryEligibilityService.decide(run, retryInputs);
        return new AnalysisRunSummaryResponse(
            run.getId(), run.getStatus(), run.getDraftRevision(), run.getGuidanceRevision(),
            run.getDiscoveryMaxConcurrency(), run.getDiscoverySourceTimeoutMillis(),
            run.getDiscoveryRequestTimeoutMillis(), run.getTotalSources(),
            run.getSucceededSources(), run.getFailedSources(), isAnalysisCurrent(draft, run), run.getAggregateRevisionId(),
            run.getFailureCategory(), run.isRetryable(), retryDecision.canRetry(),
            run.getRetryOfRunId(), run.getCreatedAt(),
            run.getStartedAt(), run.getCompletedAt(), analysisLocation(run));
    }

    public boolean isAnalysisCurrent(SchemaDraftNode draft, SchemaDraftAnalysisRunNode run) {
        return isAnalysisCurrent(draft, run, membershipFingerprint(draft == null ? null : draft.getId()));
    }

    private boolean isAnalysisCurrent(
        SchemaDraftNode draft, SchemaDraftAnalysisRunNode run, String currentMembershipFingerprint
    ) {
        if (draft == null) {
            return false;
        }
        if (run.getAggregateRevisionId() != null && run.getAggregateRevisionId().equals(draft.getCurrentAggregateId())) {
            return true;
        }
        return run.getStatus() == SchemaDraftAnalysisStatus.RUNNING
            && run.getDraftRevision() == draft.getRevision()
            && run.getGuidanceRevision() == draft.getGuidanceRevision()
            && java.util.Objects.equals(run.getGuidanceFingerprint(), draft.getGuidanceFingerprint())
            && java.util.Objects.equals(run.getSourceMembershipFingerprint(), currentMembershipFingerprint);
    }

    private String membershipFingerprint(String draftId) {
        if (draftId == null) {
            return jsonSupport.fingerprint("");
        }
        List<SchemaDraftSourceNode> sources = sourceRepository.findByDraftIdAndStatusOrderByCreatedAtAsc(
            draftId, SchemaDraftSourceStatus.ACTIVE);
        return membershipFingerprint(sources);
    }

    private String membershipFingerprint(List<SchemaDraftSourceNode> sources) {
        String value = sources.stream().sorted(Comparator.comparing(SchemaDraftSourceNode::getId))
            .map(source -> source.getId() + ":" + source.getRevision() + ":" + source.getSha256())
            .reduce((left, right) -> left + "|" + right).orElse("");
        return jsonSupport.fingerprint(value);
    }

    private SchemaDraftAnalysisRunNode newerAnalysis(
        SchemaDraftAnalysisRunNode left, SchemaDraftAnalysisRunNode right
    ) {
        int created = left.getCreatedAt().compareTo(right.getCreatedAt());
        if (created != 0) {
            return created > 0 ? left : right;
        }
        return left.getId().compareTo(right.getId()) > 0 ? left : right;
    }

    private String analysisLocation(SchemaDraftAnalysisRunNode run) {
        return "/api/v1/knowledge-bases/" + run.getKnowledgeBaseId() + "/schema-drafts/" + run.getDraftId()
            + "/analysis-runs/" + run.getId();
    }

    public record WorkflowReferences(
        AnalysisWorkflowReference currentAnalysis,
        EvaluationWorkflowReference latestEvaluation,
        ReprocessingWorkflowReference latestReprocessing
    ) { }
}
