package io.github.vfedoriv.graphrag.schemas.drafts.application;


import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftSchemaLookup;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftKnowledgeBases;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisRunSummaryResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisWorkflowReference;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.EvaluationRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.EvaluationRunSummaryResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.EvaluationWorkflowReference;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.ReprocessingWorkflowReference;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
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
    private final SchemaDraftEvaluationRunRepository evaluationRepository;
    private final SchemaReprocessingPlanRepository reprocessingRepository;
    private final SchemaDraftSourceRepository sourceRepository;
    private final DraftKnowledgeBases knowledgeBaseRepository;
    private final DraftSchemaLookup schemaRepository;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService;

    public SchemaDraftWorkflowNavigationService(
        SchemaDraftAnalysisRunRepository analysisRepository,
        SchemaDraftEvaluationRunRepository evaluationRepository,
        SchemaReprocessingPlanRepository reprocessingRepository,
        SchemaDraftSourceRepository sourceRepository,
        DraftKnowledgeBases knowledgeBaseRepository,
        DraftSchemaLookup schemaRepository,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService
    ) {
        this.analysisRepository = analysisRepository;
        this.evaluationRepository = evaluationRepository;
        this.reprocessingRepository = reprocessingRepository;
        this.sourceRepository = sourceRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaRepository = schemaRepository;
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
    public EvaluationRunPageResponse evaluationPage(SchemaDraftNode draft, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<SchemaDraftEvaluationRunNode> runs = evaluationRepository.findPageByDraftId(
            draft.getId(), PageRequest.of(boundedPage, boundedSize));
        List<EvaluationRunSummaryResponse> content = runs.getContent().stream()
            .map(run -> evaluationSummary(draft, run)).toList();
        return new EvaluationRunPageResponse(boundedPage, boundedSize, runs.getTotalElements(), content);
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
        Map<String, SchemaDraftEvaluationRunNode> evaluations = evaluationRepository.findLatestForDraftIds(draftIds).stream()
            .collect(Collectors.toMap(SchemaDraftEvaluationRunNode::getDraftId, Function.identity()));
        Map<String, SchemaReprocessingPlanNode> plans = reprocessingRepository.findLatestForDraftIds(draftIds).stream()
            .collect(Collectors.toMap(SchemaReprocessingPlanNode::getDraftId, Function.identity()));
        Map<String, Boolean> targetCurrent = targetCurrent(plans.values().stream().toList());
        Map<String, WorkflowReferences> result = new HashMap<>();
        for (SchemaDraftNode draft : drafts) {
            SchemaDraftAnalysisRunNode analysis = analyses.get(draft.getId());
            SchemaDraftEvaluationRunNode evaluation = evaluations.get(draft.getId());
            SchemaReprocessingPlanNode plan = plans.get(draft.getId());
            result.put(draft.getId(), new WorkflowReferences(
                analysis == null ? null : new AnalysisWorkflowReference(
                    analysis.getId(), analysis.getStatus(), true, analysisLocation(analysis)),
                evaluation == null ? null : new EvaluationWorkflowReference(
                    evaluation.getId(), evaluation.getStatus(), evaluationCurrent(draft, evaluation), true,
                    evaluationLocation(evaluation)),
                plan == null ? null : new ReprocessingWorkflowReference(
                    plan.getId(), plan.getStatus(), targetCurrent.getOrDefault(plan.getId(), false), true,
                    reprocessingLocation(plan))));
        }
        return Map.copyOf(result);
    }

    public boolean evaluationCurrent(SchemaDraftNode draft, SchemaDraftEvaluationRunNode run) {
        return draft != null && run.getDraftRevision() == draft.getRevision()
            && run.getAggregateRevisionId() != null
            && run.getAggregateRevisionId().equals(draft.getCurrentAggregateId())
            && run.getProjectionContentHash() != null;
    }

    public boolean targetCurrent(SchemaReprocessingPlanNode plan) {
        String activeSchemaId = knowledgeBaseRepository.activeSchemaId(plan.getKnowledgeBaseId()).orElse(null);
        SchemaSnapshot schema = schemaRepository.findById(plan.getSchemaId()).orElse(null);
        return schema != null && plan.getSchemaId().equals(activeSchemaId)
            && plan.getSchemaContentHash().equals(schema.contentHash());
    }

    public Map<String, Boolean> targetCurrent(List<SchemaReprocessingPlanNode> plans) {
        if (plans.isEmpty()) {
            return Map.of();
        }
        Map<String, String> activeSchemas = new HashMap<>();
        plans.stream().map(SchemaReprocessingPlanNode::getKnowledgeBaseId).distinct()
            .forEach(id -> knowledgeBaseRepository.activeSchemaId(id).ifPresent(active -> activeSchemas.put(id, active)));
        Map<String, SchemaSnapshot> schemas = activeSchemas.keySet().stream().flatMap(id -> schemaRepository.associated(id).stream())
            .collect(Collectors.toMap(SchemaSnapshot::schemaDefinitionId, Function.identity(), (left, right) -> left));
        return plans.stream().collect(Collectors.toMap(SchemaReprocessingPlanNode::getId, plan -> {
            SchemaSnapshot schema = schemas.get(plan.getSchemaId());
            return schema != null && plan.getSchemaId().equals(activeSchemas.get(plan.getKnowledgeBaseId()))
                && plan.getSchemaContentHash().equals(schema.contentHash());
        }));
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

    private EvaluationRunSummaryResponse evaluationSummary(
        SchemaDraftNode draft, SchemaDraftEvaluationRunNode run
    ) {
        boolean terminal = run.getStatus() != SchemaDraftEvaluationStatus.QUEUED
            && run.getStatus() != SchemaDraftEvaluationStatus.RUNNING;
        return new EvaluationRunSummaryResponse(
            run.getId(), run.getStatus(), run.getDraftRevision(), run.getAggregateRevisionId(),
            run.getProjectionContentHash(), run.getAiProfileId(), run.getAiProfileRevision(), run.getPromptRevision(),
            run.getContractRevision(), run.getRetryOfRunId(), run.getTotalDocuments(), run.getSucceededDocuments(),
            run.getFailedDocuments(), run.getStaleDocuments(), evaluationCurrent(draft, run),
            terminal && draft.getStatus() == SchemaDraftStatus.OPEN
                && run.getDraftRevision() == draft.getRevision(), run.getFailureCategory(),
            run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt(), evaluationLocation(run));
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

    private String evaluationLocation(SchemaDraftEvaluationRunNode run) {
        return "/api/v1/knowledge-bases/" + run.getKnowledgeBaseId() + "/schema-drafts/" + run.getDraftId()
            + "/evaluation-runs/" + run.getId();
    }

    private String reprocessingLocation(SchemaReprocessingPlanNode plan) {
        return "/api/v1/knowledge-bases/" + plan.getKnowledgeBaseId() + "/reprocessing-plans/" + plan.getId();
    }

    public record WorkflowReferences(
        AnalysisWorkflowReference currentAnalysis,
        EvaluationWorkflowReference latestEvaluation,
        ReprocessingWorkflowReference latestReprocessing
    ) { }
}
