package io.github.vfedoriv.graphrag.search.retrieval.application;

import io.github.vfedoriv.graphrag.search.retrieval.domain.LuceneQueryCompiler;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.indexes.contracts.LexicalIndexRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository.RawCandidate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LexicalTextRetriever {

    private final LexicalIndexRepository lexicalIndexRepository;
    private final TextChunkRetrievalRepository retrievalRepository;
    private final LuceneQueryCompiler queryCompiler;

    public LexicalTextRetriever(
        LexicalIndexRepository lexicalIndexRepository,
        TextChunkRetrievalRepository retrievalRepository,
        LuceneQueryCompiler queryCompiler
    ) {
        this.lexicalIndexRepository = lexicalIndexRepository;
        this.retrievalRepository = retrievalRepository;
        this.queryCompiler = queryCompiler;
    }

    public Result retrieve(Request request) {
        long startNanos = System.nanoTime();
        if (request.expired()) {
            return failed(Status.DEADLINE_EXCEEDED, startNanos, null);
        }
        try {
            if (request.subqueries().isEmpty()) {
                return completed(List.of(), startNanos);
            }
            lexicalIndexRepository.ensureOnline(request.knowledgeBaseId(), request.deadline());
            List<Candidate> candidates = new ArrayList<>();
            for (Subquery subquery : request.subqueries()) {
                if (request.expired()) {
                    throw new IllegalStateException("Lexical retrieval deadline exceeded");
                }
                List<RawCandidate> rows = retrievalRepository.findLexical(
                    lexicalIndexRepository.indexName(request.knowledgeBaseId()),
                    request.knowledgeBaseId(),
                    queryCompiler.compile(subquery.text()),
                    request.candidateLimit()
                );
                for (int index = 0; index < rows.size(); index++) {
                    RawCandidate row = rows.get(index);
                    candidates.add(new Candidate(
                        Branch.LEXICAL,
                        row.source(),
                        subquery.id(),
                        index + 1,
                        row.score(),
                        request.includeText() ? row.sourceText() : null
                    ));
                }
            }
            return completed(candidates, startNanos);
        } catch (RuntimeException exception) {
            Status status = request.expired() ? Status.DEADLINE_EXCEEDED : Status.FAILED;
            return failed(status, startNanos, exception.getClass().getSimpleName());
        }
    }

    private Result completed(List<Candidate> candidates, long startNanos) {
        return new Result(
            Branch.LEXICAL,
            candidates,
            new Diagnostics(Status.COMPLETED, LogMetadata.elapsedMillis(startNanos), candidates.size(), null)
        );
    }

    private Result failed(Status status, long startNanos, String category) {
        return new Result(
            Branch.LEXICAL,
            List.of(),
            new Diagnostics(status, LogMetadata.elapsedMillis(startNanos), 0, category)
        );
    }
}
