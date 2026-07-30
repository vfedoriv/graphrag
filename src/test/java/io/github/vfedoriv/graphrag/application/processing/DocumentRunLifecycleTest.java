package io.github.vfedoriv.graphrag.application.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.document.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.document.chunking.FixedCharacterChunkingStrategy;
import io.github.vfedoriv.graphrag.document.chunking.Utf8ByteTokenEstimator;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.service.DocumentFormatDetection;
import io.github.vfedoriv.graphrag.service.DocumentProcessingOptionSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentRunLifecycleTest {

    @Test
    void processingRetryReferencesTheImmediatelyPrecedingFailedRun() {
        DocumentProcessingRunRepository repository = mock(DocumentProcessingRunRepository.class);
        DocumentProcessingRunNode prior = new DocumentProcessingRunNode();
        prior.setId("processing-prior");
        prior.setDocumentId("doc-1");
        prior.setStatus(DocumentProcessingRunStatus.FAILED);
        prior.setRetryCount(2);
        when(repository.findByDocumentIdOrderByStartedAtAsc("doc-1")).thenReturn(List.of(prior));
        when(repository.save(any(DocumentProcessingRunNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProcessingRunLifecycle lifecycle = new ProcessingRunLifecycle(repository, new ObjectMapper());
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        document.setSha256("a".repeat(64));
        DocumentProcessingOptionSet options = new DocumentProcessingOptionSet(
            new DocumentFormatDetection("text", "TXT"),
            Map.of(),
            Map.of(),
            Map.of()
        );

        DocumentProcessingRunNode retry = lifecycle.start(document, options);

        assertThat(retry.getRetryOfRunId()).isEqualTo("processing-prior");
        assertThat(retry.getRetryCount()).isEqualTo(3);
    }

    @Test
    void extractionRetryReferencesTheImmediatelyPrecedingFailedRun() {
        ExtractionRunRepository repository = mock(ExtractionRunRepository.class);
        ExtractionRunNode prior = new ExtractionRunNode();
        prior.setId("extraction-prior");
        prior.setDocumentId("doc-1");
        prior.setStatus(ExtractionRunStatus.FAILED);
        prior.setRetryCount(1);
        when(repository.findByDocumentIdOrderByStartedAtAsc("doc-1")).thenReturn(List.of(prior));
        when(repository.save(any(ExtractionRunNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExtractionRunLifecycle lifecycle = new ExtractionRunLifecycle(repository);

        ExtractionRunNode retry = lifecycle.start("doc-1", "schema-1", "chat:model");

        assertThat(retry.getRetryOfRunId()).isEqualTo("extraction-prior");
        assertThat(retry.getRetryCount()).isEqualTo(2);
    }

    @Test
    void processingRunSnapshotsChunkingContextAtStart() {
        DocumentProcessingRunRepository repository = mock(DocumentProcessingRunRepository.class);
        when(repository.findByDocumentIdOrderByStartedAtAsc("doc-1")).thenReturn(List.of());
        when(repository.save(any(DocumentProcessingRunNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProcessingRunLifecycle lifecycle = new ProcessingRunLifecycle(repository, new ObjectMapper());
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        document.setSha256("a".repeat(64));
        DocumentProcessingOptionSet options = new DocumentProcessingOptionSet(
            new DocumentFormatDetection("text", "TXT"),
            Map.of(),
            Map.of(),
            Map.of()
        );
        ChunkingContext context = ChunkingContext.create(
            FixedCharacterChunkingStrategy.NAME,
            FixedCharacterChunkingStrategy.REVISION,
            800,
            80,
            4000,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "plain-text-v1"
        );

        DocumentProcessingRunNode run = lifecycle.start(document, options, context);

        assertThat(run.getChunkStrategy()).isEqualTo(context.strategyName());
        assertThat(run.getChunkSettingsHash()).isEqualTo(context.settingsHash().value());
        assertThat(run.getTokenizerId()).isEqualTo("utf8-byte-v1");
        assertThat(run.getTokenCountMode()).isEqualTo("CONSERVATIVE");
        assertThat(run.getEffectiveChunkerRevision()).isEqualTo(context.effectiveRevision().value());
    }
}
