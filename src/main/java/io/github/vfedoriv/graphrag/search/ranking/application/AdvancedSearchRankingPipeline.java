package io.github.vfedoriv.graphrag.search.ranking.application;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchGraphExpansionService;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchParentContextService;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchFusionService;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchDiversitySelector;
import io.github.vfedoriv.graphrag.search.ranking.adapters.model.AdvancedSearchReranker;
import io.github.vfedoriv.graphrag.search.answering.domain.QueryEvidenceAssemblyService;

import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchDiversitySelector.SelectionResult;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchFusionService.FusionOptions;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchFusionService.FusionResult;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchGraphExpansionService.ExpansionResult;
import io.github.vfedoriv.graphrag.search.retrieval.application.AdvancedSearchParentContextService.ExpansionOptions;
import io.github.vfedoriv.graphrag.search.ranking.adapters.model.AdvancedSearchReranker.RerankResult;
import io.github.vfedoriv.graphrag.search.answering.domain.QueryEvidenceAssemblyService.AdvancedQueryEvidenceAssembly;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchRankingPipeline {

    private final AdvancedSearchFusionService fusionService;
    private final AdvancedSearchGraphExpansionService graphExpansionService;
    private final AdvancedSearchParentContextService parentContextService;
    private final AdvancedSearchReranker reranker;
    private final AdvancedSearchDiversitySelector diversitySelector;
    private final QueryEvidenceAssemblyService evidenceAssemblyService;
    private final AiObservationService observations;

    public AdvancedSearchRankingPipeline(
        AdvancedSearchFusionService fusionService,
        AdvancedSearchGraphExpansionService graphExpansionService,
        AdvancedSearchParentContextService parentContextService,
        AdvancedSearchReranker reranker,
        AdvancedSearchDiversitySelector diversitySelector,
        QueryEvidenceAssemblyService evidenceAssemblyService,
        AiObservationService observations
    ) {
        this.fusionService = fusionService;
        this.graphExpansionService = graphExpansionService;
        this.parentContextService = parentContextService;
        this.reranker = reranker;
        this.diversitySelector = diversitySelector;
        this.evidenceAssemblyService = evidenceAssemblyService;
        this.observations = observations;
    }

    public RankingResult rank(RankingRequest request) {
        FusionResult fusion = observe("advanced-search.fusion", () ->
            fusionService.fuse(request.textResults(), request.graphResult(), request.fusionOptions()));
        ExpansionResult graphExpansion = observe("advanced-search.expansion.graph", () -> graphExpansionService.expand(
            request.knowledgeBaseId(),
            fusion.candidates(),
            request.graphExpansionSeedLimit(),
            request.graphExpansionFactLimit()
        ));
        AdvancedSearchParentContextService.ExpansionResult parentExpansion = observe(
            "advanced-search.expansion.parent", () -> parentContextService.expand(
            request.knowledgeBaseId(),
            graphExpansion.candidates(),
            request.parentContextOptions()
        ));
        RerankResult reranked = observe("advanced-search.reranking", () -> reranker.rerank(
            request.query(),
            parentExpansion.candidates(),
            request.rerankPoolSize()
        ));
        SelectionResult selected = diversitySelector.select(
            reranked.candidates(),
            request.maximumEvidence(),
            request.perDocumentCap(),
            request.comparisonPolicy()
        );
        AdvancedQueryEvidenceAssembly assembly = evidenceAssemblyService.assembleAdvanced(selected.candidates());
        return new RankingResult(
            assembly.candidates(),
            assembly,
            fusion.diagnostics(),
            graphExpansion.diagnostics(),
            parentExpansion.diagnostics(),
            reranked.diagnostics(),
            selected.diagnostics()
        );
    }

    private <T> T observe(String workflow, Supplier<T> operation) {
        try (AiObservationScope scope = observations.startWorkflow(
            new AiWorkflowContext(workflow, null, Map.of()))) {
            try {
                T result = operation.get();
                scope.success();
                return result;
            } catch (RuntimeException exception) {
                scope.error(exception);
                throw exception;
            }
        }
    }

    public record RankingRequest(
        String knowledgeBaseId,
        String query,
        List<Result> textResults,
        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result graphResult,
        FusionOptions fusionOptions,
        int graphExpansionSeedLimit,
        int graphExpansionFactLimit,
        ExpansionOptions parentContextOptions,
        int rerankPoolSize,
        int maximumEvidence,
        int perDocumentCap,
        boolean comparisonPolicy
    ) {
        public RankingRequest {
            if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
                throw new IllegalArgumentException("knowledgeBaseId must not be blank");
            }
            query = query == null ? "" : query;
            textResults = textResults == null ? List.of() : List.copyOf(textResults);
            fusionOptions = fusionOptions == null ? FusionOptions.defaults() : fusionOptions;
            parentContextOptions = parentContextOptions == null ? ExpansionOptions.defaults() : parentContextOptions;
        }
    }

    public record RankingResult(
        List<EvidenceCandidate> candidates,
        AdvancedQueryEvidenceAssembly evidence,
        AdvancedSearchFusionService.FusionDiagnostics fusionDiagnostics,
        AdvancedSearchGraphExpansionService.ExpansionDiagnostics graphExpansionDiagnostics,
        AdvancedSearchParentContextService.ExpansionDiagnostics parentContextDiagnostics,
        AdvancedSearchReranker.RerankDiagnostics rerankDiagnostics,
        AdvancedSearchDiversitySelector.SelectionDiagnostics selectionDiagnostics
    ) { }
}
