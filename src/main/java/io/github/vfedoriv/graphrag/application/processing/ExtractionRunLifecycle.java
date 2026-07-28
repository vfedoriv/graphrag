package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.service.GraphExtractionCleanupSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

@Component
public class ExtractionRunLifecycle {
    private final ExtractionRunRepository repository;

    public ExtractionRunLifecycle(ExtractionRunRepository repository) {
        this.repository = repository;
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractionRunNode start(String documentId, String schemaId, String model) {
        ExtractionRunNode run = new ExtractionRunNode();
        run.setId(UUID.randomUUID().toString());
        run.setDocumentId(documentId);
        run.setSchemaId(schemaId);
        run.setModel(model);
        run.setStatus(ExtractionRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        applyRetryMetadata(run);
        return repository.save(run);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractionRunNode complete(ExtractionRunNode run) {
        ExtractionRunNode current = current(run.getId());
        current.setStatus(ExtractionRunStatus.COMPLETED);
        current.setCompletedAt(Instant.now());
        current.setErrorMessage(null);
        current.setClaimedBy(null);
        current.setClaimUntil(null);
        return repository.save(current);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractionRunNode fail(ExtractionRunNode run, Exception error) {
        ExtractionRunNode current = current(run.getId());
        current.setStatus(ExtractionRunStatus.FAILED);
        current.setCompletedAt(Instant.now());
        current.setErrorMessage(GraphExtractionCleanupSupport.toNonBlankErrorMessage(error));
        current.setClaimedBy(null);
        current.setClaimUntil(null);
        return repository.save(current);
    }

    private ExtractionRunNode current(String runId) {
        return repository.findById(runId)
            .orElseThrow(() -> new IllegalStateException("Extraction run not found: " + runId));
    }

    private void applyRetryMetadata(ExtractionRunNode run) {
        List<ExtractionRunNode> history = repository.findByDocumentIdOrderByStartedAtAsc(run.getDocumentId());
        if (history.isEmpty()) {
            return;
        }
        ExtractionRunNode prior = history.getLast();
        if (prior.getStatus() == ExtractionRunStatus.FAILED) {
            run.setRetryOfRunId(prior.getId());
            run.setRetryCount(prior.getRetryCount() + 1);
        }
    }
}
