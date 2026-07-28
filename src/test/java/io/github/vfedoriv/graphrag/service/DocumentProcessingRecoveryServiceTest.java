package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.application.processing.ExtractionRunLifecycle;
import io.github.vfedoriv.graphrag.application.processing.ProcessingRunLifecycle;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentProcessingRecoveryServiceTest {

    @Test
    void marksOnlyStaleProcessingDocumentsFailed() {
        DocumentProcessingRunRepository processingRuns = mock(DocumentProcessingRunRepository.class);
        ExtractionRunRepository extractionRuns = mock(ExtractionRunRepository.class);
        DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
        ProcessingRunLifecycle processingLifecycle = mock(ProcessingRunLifecycle.class);
        ExtractionRunLifecycle extractionLifecycle = mock(ExtractionRunLifecycle.class);
        GraphArtifactCleanupService cleanup = mock(GraphArtifactCleanupService.class);
        DocumentProcessingRunNode stale = processingRun("run-stale", "doc-stale");
        DocumentUploadNode staleDocument = document("doc-stale", DocumentStatus.EMBEDDING);
        DocumentUploadNode unrelated = document("doc-unrelated", DocumentStatus.UPLOADED);
        when(processingRuns.findStaleRunningBefore(any(), eq(100))).thenReturn(List.of(stale));
        when(extractionRuns.findStaleRunningBefore(any(), eq(100))).thenReturn(List.of());
        when(documents.findById("doc-stale")).thenReturn(Optional.of(staleDocument));
        DocumentProcessingRecoveryService service = new DocumentProcessingRecoveryService(
            processingRuns, extractionRuns, documents, processingLifecycle, extractionLifecycle, cleanup
        );

        service.recoverStaleRuns();

        verify(processingLifecycle).fail(eq(stale), any(IllegalStateException.class));
        verify(documents).save(staleDocument);
        verify(documents, never()).save(unrelated);
        assertThat(staleDocument.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(staleDocument.getErrorMessage()).isEqualTo("Processing interrupted before completion");
    }

    @Test
    void cleansOnlyEvidenceOwnedByTheStaleExtractionRun() {
        DocumentProcessingRunRepository processingRuns = mock(DocumentProcessingRunRepository.class);
        ExtractionRunRepository extractionRuns = mock(ExtractionRunRepository.class);
        DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
        ProcessingRunLifecycle processingLifecycle = mock(ProcessingRunLifecycle.class);
        ExtractionRunLifecycle extractionLifecycle = mock(ExtractionRunLifecycle.class);
        GraphArtifactCleanupService cleanup = mock(GraphArtifactCleanupService.class);
        ExtractionRunNode stale = extractionRun("extraction-stale", "doc-stale");
        when(processingRuns.findStaleRunningBefore(any(), eq(100))).thenReturn(List.of());
        when(extractionRuns.findStaleRunningBefore(any(), eq(100))).thenReturn(List.of(stale));
        DocumentProcessingRecoveryService service = new DocumentProcessingRecoveryService(
            processingRuns, extractionRuns, documents, processingLifecycle, extractionLifecycle, cleanup
        );

        service.recoverStaleRuns();

        verify(extractionLifecycle).fail(eq(stale), any(IllegalStateException.class));
        verify(cleanup).cleanupExtractionRuns("doc-stale", List.of("extraction-stale"));
        verify(cleanup, never()).cleanupDocumentArtifacts(any());
    }

    private DocumentProcessingRunNode processingRun(String id, String documentId) {
        DocumentProcessingRunNode run = new DocumentProcessingRunNode();
        run.setId(id);
        run.setDocumentId(documentId);
        run.setStage("EMBEDDING");
        return run;
    }

    private ExtractionRunNode extractionRun(String id, String documentId) {
        ExtractionRunNode run = new ExtractionRunNode();
        run.setId(id);
        run.setDocumentId(documentId);
        return run;
    }

    private DocumentUploadNode document(String id, DocumentStatus status) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setStatus(status);
        return document;
    }
}
