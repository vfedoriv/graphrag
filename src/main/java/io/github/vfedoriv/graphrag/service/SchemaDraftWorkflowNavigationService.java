package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AnalysisRunSummaryResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AnalysisWorkflowReference;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationRunPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationRunSummaryResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationWorkflowReference;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ReprocessingWorkflowReference;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
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
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final SchemaRegistryService schemaRegistryService;
    private final SchemaDraftJsonSupport jsonSupport;
    private final SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService;

    public SchemaDraftWorkflowNavigationService(
        SchemaDraftAnalysisRunRepository analysisRepository,
        SchemaDraftEvaluationRunRepository evaluationRepository,
        SchemaReprocessingPlanRepository reprocessingRepository,
        SchemaDraftSourceRepository sourceRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        SchemaDefinitionRepository schemaRepository,
        SchemaRegistryService schemaRegistryService,
        SchemaDraftJsonSupport jsonSupport,
        SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService
    ) {
        this.analysisRepository = analysisRepository;
        this.evaluationRepository = evaluationRepository;
        this.reprocessingRepository = reprocessingRepository;
        this.sourceRepository = sourceRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaRepository = schemaRepository;
        this.schemaRegistryService = schemaRegistryService;
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
        KnowledgeBaseNode knowledgeBase = knowledgeBaseRepository.findById(plan.getKnowledgeBaseId()).orElse(null);
        SchemaDefinitionNode schema = schemaRepository.findById(plan.getSchemaId()).orElse(null);
        return knowledgeBase != null && schema != null && plan.getSchemaId().equals(knowledgeBase.getActiveSchemaId())
            && plan.getSchemaContentHash().equals(schema.getContentHash());
    }

    public Map<String, Boolean> targetCurrent(List<SchemaReprocessingPlanNode> plans) {
        if (plans.isEmpty()) {
            return Map.of();
        }
        Map<String, KnowledgeBaseNode> knowledgeBases = plans.stream().map(SchemaReprocessingPlanNode::getKnowledgeBaseId)
            .distinct().map(id -> knowledgeBaseRepository.findById(id).orElse(null))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toMap(KnowledgeBaseNode::getId, Function.identity()));
        Map<String, SchemaDefinitionNode> schemas = knowledgeBases.keySet().stream()
            .flatMap(id -> schemaRegistryService.listSchemasByKnowledgeBase(id).stream())
            .collect(Collectors.toMap(SchemaDefinitionNode::getId, Function.identity(), (left, right) -> left));
        return plans.stream().collect(Collectors.toMap(SchemaReprocessingPlanNode::getId, plan -> {
            KnowledgeBaseNode knowledgeBase = knowledgeBases.get(plan.getKnowledgeBaseId());
            SchemaDefinitionNode schema = schemas.get(plan.getSchemaId());
            return knowledgeBase != null && schema != null
                && plan.getSchemaId().equals(knowledgeBase.getActiveSchemaId())
                && plan.getSchemaContentHash().equals(schema.getContentHash());
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
