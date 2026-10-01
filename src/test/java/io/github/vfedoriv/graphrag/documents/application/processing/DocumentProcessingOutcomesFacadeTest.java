package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentProcessingOutcomes;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DocumentProcessingOutcomesFacadeTest {
    @Test
    void matchesExactlyTheHistoricalRecoveryPredicate() throws Exception {
        DocumentProcessingRunRepository runs = mock(DocumentProcessingRunRepository.class);
        DocumentProcessingOutcomes facade = new DocumentProcessingOutcomesFacade(runs);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        DocumentProcessingRunNode run = new DocumentProcessingRunNode();
        run.setStatus(DocumentProcessingRunStatus.COMPLETED);
        run.setActiveCompleted(true);
        run.setSourceSha256("hash");
        run.setEffectiveChunkerRevision("revision");
        run.setStartedAt(start);
        when(runs.findByDocumentIdOrderByStartedAtAsc("doc")).thenReturn(List.of(run));
        DocumentProcessingOutcomes.Request migration = new DocumentProcessingOutcomes.Request("doc", "hash", "revision", start);
        assertThat(facade.completedOverwrite(migration)).isTrue();
        run.setActiveCompleted(false);
        assertThat(facade.completedOverwrite(migration)).isFalse();
        run.setActiveCompleted(true);
        run.setStatus(DocumentProcessingRunStatus.FAILED);
        assertThat(facade.completedOverwrite(migration)).isFalse();
        run.setStatus(DocumentProcessingRunStatus.COMPLETED);
        run.setSourceSha256("other");
        assertThat(facade.completedOverwrite(migration)).isFalse();
        run.setSourceSha256("hash");
        run.setEffectiveChunkerRevision("other");
        assertThat(facade.completedOverwrite(migration)).isFalse();
        assertThat(facade.completedOverwrite(new DocumentProcessingOutcomes.Request("doc", "hash", null, start))).isTrue();
        run.setEffectiveChunkerRevision("revision");
        run.setStartedAt(start.minusNanos(1));
        assertThat(facade.completedOverwrite(migration)).isFalse();
        assertThat(facade.completedOverwrite(new DocumentProcessingOutcomes.Request("doc", "hash", "revision", null))).isTrue();
        run.setStartedAt(start.plusNanos(1));
        assertThat(facade.completedOverwrite(migration)).isTrue();
        when(runs.findByDocumentIdOrderByStartedAtAsc("doc")).thenReturn(List.of());
        assertThat(facade.completedOverwrite(migration)).isFalse();
    }
}
