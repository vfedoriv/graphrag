package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.service.DocumentProcessingOptionSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Component
public class ProcessingRunLifecycle {

    private final DocumentProcessingRunRepository repository;
    private final ProcessingJsonCodec jsonCodec;

    public ProcessingRunLifecycle(DocumentProcessingRunRepository repository, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.repository = repository;
        this.jsonCodec = new ProcessingJsonCodec(objectMapper);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentProcessingRunNode start(DocumentUploadNode document, DocumentProcessingOptionSet options) {
        DocumentProcessingRunNode run = new DocumentProcessingRunNode();
        run.setId(UUID.randomUUID().toString());
        run.setDocumentId(document.getId());
        run.setKnowledgeBaseId(document.getKnowledgeBaseId());
        run.setSourceSha256(document.getSha256());
        run.setParserId(options.detection().parserId());
        run.setFileFormat(options.detection().fileFormat());
        run.setRequestedOptionsJson(jsonCodec.writeMap(options.requestedOptions()));
        run.setSavedDefaultsJson(jsonCodec.writeMap(options.savedDefaults()));
        run.setEffectiveOptionsJson(jsonCodec.writeMap(options.effectiveOptions()));
        run.setStatus(DocumentProcessingRunStatus.RUNNING);
        run.setStage("STARTED");
        run.setStartedAt(Instant.now());
        run.setActiveCompleted(false);
        applyRetryMetadata(run);
        DocumentProcessingRunNode saved = repository.save(run);
        repository.attachToDocument(document.getId(), saved.getId());
        return saved;
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentProcessingRunNode checkpoint(DocumentProcessingRunNode run, String stage) {
        run.setStage(stage);
        return repository.save(run);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentProcessingRunNode complete(DocumentProcessingRunNode run) {
        run.setStatus(DocumentProcessingRunStatus.COMPLETED);
        run.setStage("COMPLETED");
        run.setCompletedAt(Instant.now());
        run.setErrorMessage(null);
        run.setActiveCompleted(true);
        repository.deactivateOtherCompletedRuns(run.getDocumentId(), run.getId());
        return repository.save(run);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentProcessingRunNode fail(DocumentProcessingRunNode run, Exception error) {
        run.setStatus(DocumentProcessingRunStatus.FAILED);
        run.setStage("FAILED");
        run.setCompletedAt(Instant.now());
        run.setErrorMessage(error.getMessage());
        run.setActiveCompleted(false);
        return repository.save(run);
    }

    private void applyRetryMetadata(DocumentProcessingRunNode run) {
        List<DocumentProcessingRunNode> history =
            repository.findByDocumentIdOrderByStartedAtAsc(run.getDocumentId());
        if (history.isEmpty()) {
            return;
        }
        DocumentProcessingRunNode prior = history.getLast();
        if (prior.getStatus() == DocumentProcessingRunStatus.FAILED) {
            run.setRetryOfRunId(prior.getId());
            run.setRetryCount(prior.getRetryCount() + 1);
        }
    }
}
