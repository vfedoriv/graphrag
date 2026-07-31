package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository.RawCandidate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DenseTextRetriever {

    private final ProfileScopedAiClientResolver aiClientResolver;
    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingSpacePolicy embeddingSpacePolicy;
    private final EmbeddingSpaceIndexService embeddingSpaceIndexService;
    private final TextChunkRetrievalRepository retrievalRepository;

    public DenseTextRetriever(
        ProfileScopedAiClientResolver aiClientResolver,
        KnowledgeBaseService knowledgeBaseService,
        EmbeddingSpacePolicy embeddingSpacePolicy,
        EmbeddingSpaceIndexService embeddingSpaceIndexService,
        TextChunkRetrievalRepository retrievalRepository
    ) {
        this.aiClientResolver = aiClientResolver;
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingSpacePolicy = embeddingSpacePolicy;
        this.embeddingSpaceIndexService = embeddingSpaceIndexService;
        this.retrievalRepository = retrievalRepository;
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
            AiProfileNode profile = knowledgeBaseService.activeAiProfile(request.knowledgeBaseId());
            EmbeddingSpace embeddingSpace = embeddingSpacePolicy.spaceFor(profile);
            embeddingSpacePolicy.requireCompatible(request.knowledgeBaseId(), profile);
            EmbeddingClient embeddingClient = aiClientResolver.embeddingClient();
            if (embeddingClient == null) {
                throw new IllegalStateException("Embedding model is not configured");
            }
            List<String> texts = request.subqueries().stream().map(Subquery::text).toList();
            List<List<Double>> vectors = AiProfileContext.withProfile(profile.getId(), () -> embeddingClient.embed(texts));
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
