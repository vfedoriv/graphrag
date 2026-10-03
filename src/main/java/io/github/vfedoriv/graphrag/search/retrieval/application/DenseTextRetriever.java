package io.github.vfedoriv.graphrag.search.retrieval.application;

import io.github.vfedoriv.graphrag.indexes.contracts.VectorIndexes;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository.RawCandidate;
import java.util.ArrayList;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.search.retrieval.ports.SearchEmbeddingModel;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DenseTextRetriever {

    private final SearchEmbeddingModel embeddings;
    private final EmbeddingCompatibility embeddingSpacePolicy;
    private final VectorIndexes embeddingSpaceIndexService;
    private final TextChunkRetrievalRepository retrievalRepository;

    public DenseTextRetriever(SearchEmbeddingModel embeddings, EmbeddingCompatibility compatibility,
        VectorIndexes embeddingSpaceIndexService, TextChunkRetrievalRepository retrievalRepository) {
        this.embeddings = embeddings; this.embeddingSpacePolicy = compatibility;
        this.embeddingSpaceIndexService = embeddingSpaceIndexService; this.retrievalRepository = retrievalRepository;
    }

    public Result retrieve(Request request) {
        long startNanos = System.nanoTime();
        if (request.expired()) {
            return failed(Status.DEADLINE_EXCEEDED, startNanos, null);
        }
        try {
            if (request.subqueries().isEmpty() || !embeddingSpacePolicy.hasEmbeddedChunks(request.knowledgeBaseId())) {
                return completed(List.of(), startNanos);
            }
            List<String> texts = request.subqueries().stream().map(Subquery::text).toList();
            SearchEmbeddingModel.Batch batch = embeddings.embed(request.knowledgeBaseId(), texts);
            EmbeddingTarget embeddingSpace = batch.target();
            List<List<Double>> vectors = batch.vectors();
            if (vectors.size() != request.subqueries().size()) {
                throw new IllegalStateException("Embedding response size mismatch");
            }
            requireTime(request);
            embeddingSpaceIndexService.ensureIndex(request.knowledgeBaseId(), embeddingSpace);
            List<Candidate> candidates = new ArrayList<>();
            String indexName = embeddingSpaceIndexService.indexName(request.knowledgeBaseId(), embeddingSpace.id());
            for (int subqueryIndex = 0; subqueryIndex < request.subqueries().size(); subqueryIndex++) {
                requireTime(request);
                Subquery subquery = request.subqueries().get(subqueryIndex);
                List<RawCandidate> rows = retrievalRepository.findDense(
                    indexName,
                    request.knowledgeBaseId(),
                    embeddingSpace.id(),
                    vectors.get(subqueryIndex),
                    request.candidateLimit()
                );
                addCandidates(candidates, rows, subquery.id(), request.includeText());
            }
            return completed(candidates, startNanos);
        } catch (RuntimeException exception) {
            Status status = request.expired() ? Status.DEADLINE_EXCEEDED : Status.FAILED;
            return failed(status, startNanos, exception.getClass().getSimpleName());
        }
    }

    private void addCandidates(List<Candidate> candidates, List<RawCandidate> rows, String subqueryId, boolean includeText) {
        for (int index = 0; index < rows.size(); index++) {
            RawCandidate row = rows.get(index);
            candidates.add(new Candidate(
                Branch.DENSE,
                row.source(),
                subqueryId,
                index + 1,
                row.score(),
                includeText ? row.sourceText() : null
            ));
        }
    }

    private void requireTime(Request request) {
        if (request.expired()) {
            throw new IllegalStateException("Dense retrieval deadline exceeded");
        }
    }

    private Result completed(List<Candidate> candidates, long startNanos) {
        return new Result(
            Branch.DENSE,
            candidates,
            new Diagnostics(Status.COMPLETED, LogMetadata.elapsedMillis(startNanos), candidates.size(), null)
        );
    }

    private Result failed(Status status, long startNanos, String category) {
        return new Result(
            Branch.DENSE,
            List.of(),
            new Diagnostics(status, LogMetadata.elapsedMillis(startNanos), 0, category)
        );
    }
}
