package io.github.vfedoriv.graphrag.documents.application.management;

import io.github.vfedoriv.graphrag.documents.ports.DocumentArtifactCleanup;

import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationType;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentStorageMutationRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentBinaryStorage;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@Slf4j
public class DocumentStorageReconciliationService implements ApplicationRunner {
    private static final int MAX_RETRIES = 10;
    private static final int CLAIM_LIMIT = 100;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);
    private static final Duration COMPLETED_RETENTION = Duration.ofDays(7);
    private static final String WORKER_ID = "document-storage-reconciler";
    private final DocumentStorageMutationRepository mutationRepository;
    private final DocumentUploadRepository documentUploadRepository;
    private final DocumentStorageMutationService mutationService;
    private final DocumentBinaryStorage binaryStorageService;
    private final DocumentArtifactCleanup graphArtifactCleanupService;
    private final MeterRegistry meterRegistry;

    public DocumentStorageReconciliationService(DocumentStorageMutationRepository mutationRepository, DocumentUploadRepository documentUploadRepository,
        DocumentStorageMutationService mutationService, DocumentBinaryStorage binaryStorageService,
        DocumentArtifactCleanup graphArtifactCleanupService, MeterRegistry meterRegistry) {
        this.mutationRepository = mutationRepository;
        this.documentUploadRepository = documentUploadRepository;
        this.mutationService = mutationService;
        this.binaryStorageService = binaryStorageService;
        this.graphArtifactCleanupService = graphArtifactCleanupService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void run(ApplicationArguments args) { reconcilePendingMutations(); }

    @Scheduled(fixedDelay = 300_000L)
    public void reconcilePendingMutations() {
        Instant now = Instant.now();
        List<DocumentStorageMutationNode> pending = mutationRepository.claimPending(
            WORKER_ID,
            now,
            now.plus(CLAIM_LEASE),
            CLAIM_LIMIT
        );
        meterRegistry.counter("document.storage.mutations.pending").increment(pending.size());
        for (DocumentStorageMutationNode mutation : pending) { reconcile(mutation); }
        mutationRepository.deleteCompletedBefore(now.minus(COMPLETED_RETENTION));
    }

    private void reconcile(DocumentStorageMutationNode mutation) {
        try {
            if (mutation.getType() == DocumentStorageMutationType.STORE) {
                reconcileStore(mutation);
            } else if (mutation.getType() == DocumentStorageMutationType.DELETE_REPLACED_CONTENT) {
                deleteBinaryAndComplete(mutation);
            } else {
                reconcileDelete(mutation);
            }
            meterRegistry.counter("document.storage.mutations.completed", "type", mutation.getType().name()).increment();
            log.info("Document storage mutation reconciled: mutationId={}, type={}, documentId={}", mutation.getId(), mutation.getType(), mutation.getDocumentId());
        } catch (Exception ex) {
            if (mutation.getRetryCount() + 1 >= MAX_RETRIES) {
                mutationService.markPermanentlyFailed(mutation.getId(), ex);
                meterRegistry.counter("document.storage.mutations.failed", "type", mutation.getType().name()).increment();
            } else {
                mutationService.recordFailure(mutation.getId(), ex);
                meterRegistry.counter("document.storage.mutations.retried", "type", mutation.getType().name()).increment();
            }
            log.warn("Document storage reconciliation failed: mutationId={}, type={}, documentId={}, error={}", mutation.getId(), mutation.getType(), mutation.getDocumentId(), ex.getClass().getSimpleName());
        }
    }

    private void reconcileStore(DocumentStorageMutationNode mutation) throws Exception {
        if (mutation.getContentUri() == null || mutation.getContentUri().isBlank()) { mutationService.compensate(mutation.getId()); return; }
        DocumentUploadNode document = documentUploadRepository.findById(mutation.getDocumentId()).orElse(null);
        if (document != null && mutation.getContentUri().equals(document.getContentUri())) { mutationService.complete(mutation.getId()); return; }
        binaryStorageService.delete(URI.create(mutation.getContentUri()));
        mutationService.compensate(mutation.getId());
    }

    private void reconcileDelete(DocumentStorageMutationNode mutation) throws Exception {
        DocumentUploadNode document = documentUploadRepository.findById(mutation.getDocumentId()).orElse(null);
        if (document == null) { mutationService.complete(mutation.getId()); return; }
        deleteBinary(mutation);
        graphArtifactCleanupService.cleanupDocumentArtifacts(document.getId());
        documentUploadRepository.delete(document);
        mutationService.complete(mutation.getId());
    }

    private void deleteBinaryAndComplete(DocumentStorageMutationNode mutation) throws Exception {
        deleteBinary(mutation);
        mutationService.complete(mutation.getId());
    }

    private void deleteBinary(DocumentStorageMutationNode mutation) throws Exception {
        if (mutation.getContentUri() != null && !mutation.getContentUri().isBlank()) {
            URI contentUri = URI.create(mutation.getContentUri());
            binaryStorageService.deleteIfPresent(contentUri);
        }
    }
}
