package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import java.util.List;

public interface AdvancedSearchEvidenceExpansionRepository {

    List<ExpansionRow> expand(String knowledgeBaseId, List<String> seedChunkIds, int maxFacts);

    record ExpansionRow(String seedChunkId, GraphFact fact) { }
}
