package io.github.vfedoriv.graphrag.search.retrieval.ports;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import java.util.List;

public interface AdvancedSearchEvidenceExpansionRepository {

    List<ExpansionRow> expand(String knowledgeBaseId, List<String> seedChunkIds, int maxFacts);

    record ExpansionRow(String seedChunkId, GraphFact fact) { }
}
