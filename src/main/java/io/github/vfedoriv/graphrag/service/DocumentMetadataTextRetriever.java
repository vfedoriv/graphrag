package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository.RawCandidate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentMetadataTextRetriever {

    private final DocumentUploadRepository documentRepository;
    private final TextChunkRetrievalRepository retrievalRepository;

    public DocumentMetadataTextRetriever(
        DocumentUploadRepository documentRepository,
        TextChunkRetrievalRepository retrievalRepository
    ) {
        this.documentRepository = documentRepository;
        this.retrievalRepository = retrievalRepository;
    }

    public Result retrieve(Request request) {
        long startNanos = System.nanoTime();
        if (request.expired()) {
            return failed(Status.DEADLINE_EXCEEDED, startNanos, null);
        }
        try {
            if (!request.metadata().present()) {
                return completed(List.of(), startNanos);
            }
            List<DocumentUploadNode> documents = documentRepository.findByMetadata(
                request.knowledgeBaseId(),
                request.metadata().filename(),
                request.metadata().contentType(),
                request.candidateLimit()
            );
            if (request.expired()) {
                throw new IllegalStateException("Metadata retrieval deadline exceeded");
            }
            List<String> documentIds = documents.stream().map(DocumentUploadNode::getId).toList();
            List<RawCandidate> rows = retrievalRepository.findOwnedDocumentChunks(
                request.knowledgeBaseId(),
                documentIds,
                request.candidateLimit()
            );
            List<Candidate> candidates = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                RawCandidate row = rows.get(index);
                candidates.add(new Candidate(
                    Branch.METADATA,
                    row.source(),
                    null,
                    index + 1,
                    row.score(),
                    request.includeText() ? row.sourceText() : null
                ));
            }
            return completed(candidates, startNanos);
        } catch (RuntimeException exception) {
            Status status = request.expired() ? Status.DEADLINE_EXCEEDED : Status.FAILED;
            return failed(status, startNanos, exception.getClass().getSimpleName());
        }
    }

    private Result completed(List<Candidate> candidates, long startNanos) {
        return new Result(
            Branch.METADATA,
            candidates,
            new Diagnostics(Status.COMPLETED, LogMetadata.elapsedMillis(startNanos), candidates.size(), null)
        );
    }

    private Result failed(Status status, long startNanos, String category) {
        return new Result(
            Branch.METADATA,
            List.of(),
            new Diagnostics(status, LogMetadata.elapsedMillis(startNanos), 0, category)
        );
    }
}
