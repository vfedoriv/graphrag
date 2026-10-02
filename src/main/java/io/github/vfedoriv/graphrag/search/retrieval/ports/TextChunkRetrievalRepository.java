package io.github.vfedoriv.graphrag.search.retrieval.ports;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import java.util.List;

public interface TextChunkRetrievalRepository {

    record RawCandidate(SourceIdentity source, double score, String sourceText) { }

    List<RawCandidate> findDense(
        String indexName,
        String knowledgeBaseId,
        String embeddingSpaceId,
        List<Double> queryVector,
        int limit
    );

    List<RawCandidate> findLexical(
        String indexName,
        String knowledgeBaseId,
        String luceneQuery,
        int limit
    );

    List<RawCandidate> findOwnedDocumentChunks(
        String knowledgeBaseId,
        List<String> documentIds,
        int limit
    );
}
