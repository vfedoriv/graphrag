package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.application.processing.ExtractionRunLifecycle;
import io.github.vfedoriv.graphrag.application.processing.ProcessingRunLifecycle;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@Slf4j
public class DocumentProcessingRecoveryService implements ApplicationRunner {
    private static final int RECOVERY_LIMIT = 100;
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final DocumentProcessingRunRepository processingRunRepository;
    private final ExtractionRunRepository extractionRunRepository;
    private final DocumentUploadRepository documentRepository;
    private final ProcessingRunLifecycle processingRunLifecycle;
    private final ExtractionRunLifecycle extractionRunLifecycle;
    private final GraphArtifactCleanupService graphArtifactCleanupService;

    public DocumentProcessingRecoveryService(
        DocumentProcessingRunRepository processingRunRepository,
        ExtractionRunRepository extractionRunRepository,
        DocumentUploadRepository documentRepository,
        ProcessingRunLifecycle processingRunLifecycle,
        ExtractionRunLifecycle extractionRunLifecycle,
        GraphArtifactCleanupService graphArtifactCleanupService
    ) {
        this.processingRunRepository = processingRunRepository;
        this.extractionRunRepository = extractionRunRepository;
        this.documentRepository = documentRepository;
        this.processingRunLifecycle = processingRunLifecycle;
        this.extractionRunLifecycle = extractionRunLifecycle;
        this.graphArtifactCleanupService = graphArtifactCleanupService;
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverStaleRuns();
    }

    @Scheduled(fixedDelay = 300_000L)
    public void recoverStaleRuns() {
        Instant staleBefore = Instant.now().minus(STALE_AFTER);
        recoverProcessingRuns(processingRunRepository.findStaleRunningBefore(staleBefore, RECOVERY_LIMIT));
        recoverExtractionRuns(extractionRunRepository.findStaleRunningBefore(staleBefore, RECOVERY_LIMIT));
    }

    private void recoverProcessingRuns(List<DocumentProcessingRunNode> staleRuns) {
        for (DocumentProcessingRunNode run : staleRuns) {
            IllegalStateException interruption = new IllegalStateException("Processing interrupted before completion");
            processingRunLifecycle.fail(run, interruption);
            DocumentUploadNode document = documentRepository.findById(run.getDocumentId()).orElse(null);
            if (document != null && document.getStatus() != DocumentStatus.COMPLETED) {
                document.setStatus(DocumentStatus.FAILED);
                document.setErrorMessage(interruption.getMessage());
                documentRepository.save(document);
            }
            log.warn(
                "Recovered stale processing run: runId={}, documentId={}, stage={}",
                run.getId(),
                run.getDocumentId(),
                run.getStage()
            );
        }
    }

    private void recoverExtractionRuns(List<ExtractionRunNode> staleRuns) {
        for (ExtractionRunNode run : staleRuns) {
            IllegalStateException interruption = new IllegalStateException("Extraction interrupted before completion");
            extractionRunLifecycle.fail(run, interruption);
            graphArtifactCleanupService.cleanupExtractionRuns(run.getDocumentId(), List.of(run.getId()));
            log.warn(
                "Recovered stale extraction run: runId={}, documentId={}",
                run.getId(),
                run.getDocumentId()
            );
        }
    }
}
