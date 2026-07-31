package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.service.AdvancedSearchDiversitySelector.SelectionResult;
import io.github.vfedoriv.graphrag.service.AdvancedSearchFusionService.FusionOptions;
import io.github.vfedoriv.graphrag.service.AdvancedSearchFusionService.FusionResult;
import io.github.vfedoriv.graphrag.service.AdvancedSearchGraphExpansionService.ExpansionResult;
import io.github.vfedoriv.graphrag.service.AdvancedSearchParentContextService.ExpansionOptions;
import io.github.vfedoriv.graphrag.service.AdvancedSearchReranker.RerankResult;
import io.github.vfedoriv.graphrag.service.QueryEvidenceAssemblyService.AdvancedQueryEvidenceAssembly;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchRankingPipeline {

    private final AdvancedSearchFusionService fusionService;
    private final AdvancedSearchGraphExpansionService graphExpansionService;
    private final AdvancedSearchParentContextService parentContextService;
    private final AdvancedSearchReranker reranker;
    private final AdvancedSearchDiversitySelector diversitySelector;
    private final QueryEvidenceAssemblyService evidenceAssemblyService;

    public AdvancedSearchRankingPipeline(
        AdvancedSearchFusionService fusionService,
        AdvancedSearchGraphExpansionService graphExpansionService,
        AdvancedSearchParentContextService parentContextService,
        AdvancedSearchReranker reranker,
        AdvancedSearchDiversitySelector diversitySelector,
        QueryEvidenceAssemblyService evidenceAssemblyService
    ) {
        this.fusionService = fusionService;
        this.graphExpansionService = graphExpansionService;
        this.parentContextService = parentContextService;
        this.reranker = reranker;
        this.diversitySelector = diversitySelector;
        this.evidenceAssemblyService = evidenceAssemblyService;
    }

    public RankingResult rank(RankingRequest request) {
        FusionResult fusion = fusionService.fuse(request.textResults(), request.graphResult(), request.fusionOptions());
        ExpansionResult graphExpansion = graphExpansionService.expand(
            request.knowledgeBaseId(),
            fusion.candidates(),
            request.graphExpansionSeedLimit(),
            request.graphExpansionFactLimit()
        );
        AdvancedSearchParentContextService.ExpansionResult parentExpansion = parentContextService.expand(
            request.knowledgeBaseId(),
            graphExpansion.candidates(),
            request.parentContextOptions()
        );
        RerankResult reranked = reranker.rerank(
            request.query(),
            parentExpansion.candidates(),
            request.rerankPoolSize()
        );
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

    public record RankingRequest(
        String knowledgeBaseId,
        String query,
        List<Result> textResults,
        io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Result graphResult,
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
