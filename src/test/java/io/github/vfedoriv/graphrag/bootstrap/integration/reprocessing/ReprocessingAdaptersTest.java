package io.github.vfedoriv.graphrag.bootstrap.integration.reprocessing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentProcessingOutcomes;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentExecutor;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingProcessingOutcomeReader;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReprocessingAdaptersTest {
    @Test
    void mapsBothExecutionTargetsAndEveryResultCategory() throws Exception {
        AtomicReference<DocumentReprocessing.Request> captured = new AtomicReference<>();
        AtomicReference<DocumentReprocessing.Result> result = new AtomicReference<>(
            new DocumentReprocessing.Result(DocumentReprocessing.Status.SUCCEEDED, null));
        AtomicReference<DocumentReprocessing.Source> capturedSource = new AtomicReference<>();
        DocumentReprocessing documents = new DocumentReprocessing() {
            public boolean sourceMatches(Source source) {
                capturedSource.set(source);
                return source.expectedSourceSha256().equals("hash");
            }
            public Result execute(Request request) { captured.set(request); return result.get(); }
        };
        ReprocessingDocumentExecutor adapter = new ReprocessingDocumentExecutionAdapter(documents);
        assertThat(adapter.sourceMatches(new ReprocessingDocumentExecutor.Source("kb", "doc", "hash"))).isTrue();
        assertThat(capturedSource.get()).isEqualTo(new DocumentReprocessing.Source("kb", "doc", "hash"));
        assertThat(adapter.sourceMatches(new ReprocessingDocumentExecutor.Source("kb", "doc", "changed"))).isFalse();
        ReprocessingDocumentExecutor.Request activation = new ReprocessingDocumentExecutor.Request(
            "kb", "doc", "hash", "scope", new ReprocessingDocumentExecutor.Activation(Map.of("requested", 7)));
        assertThat(adapter.execute(activation)).isEqualTo(new ReprocessingDocumentExecutor.Result(
            ReprocessingDocumentExecutor.Status.SUCCEEDED, null));
        assertThat(captured.get()).isEqualTo(new DocumentReprocessing.Request("kb", "doc", "hash", "scope",
            new DocumentReprocessing.Activation(Map.of("requested", 7))));
        ReprocessingDocumentExecutor.Request migration = new ReprocessingDocumentExecutor.Request(
            "kb", "doc", "hash", "scope", new ReprocessingDocumentExecutor.Migration(
                "profile", 23, "space", "schema", "schema-hash",
                new ReprocessingDocumentExecutor.ChunkTarget("strategy", "strategy-rev", 1, 2, 3, 4, 5, 6, 7, 8,
                    "tokenizer", "tokenizer-rev", "count-mode", "representation", "settings"),
                new ReprocessingDocumentExecutor.DocumentTarget("source", "parser", "parser-rev", "format",
                    "effective", Map.of("saved", 9))));
        adapter.execute(migration);
        assertThat(captured.get()).isEqualTo(new DocumentReprocessing.Request("kb", "doc", "hash", "scope",
            new DocumentReprocessing.Migration("profile", 23, "space", "schema", "schema-hash",
                new DocumentReprocessing.ChunkTarget("strategy", "strategy-rev", 1, 2, 3, 4, 5, 6, 7, 8,
                    "tokenizer", "tokenizer-rev", "count-mode", "representation", "settings"),
                new DocumentReprocessing.DocumentTarget("source", "parser", "parser-rev", "format", "effective", Map.of("saved", 9)))));
        result.set(new DocumentReprocessing.Result(DocumentReprocessing.Status.STALE_SOURCE, "SOURCE_CHANGED"));
        assertThat(adapter.execute(activation)).isEqualTo(new ReprocessingDocumentExecutor.Result(
            ReprocessingDocumentExecutor.Status.STALE_SOURCE, "SOURCE_CHANGED"));
        result.set(new DocumentReprocessing.Result(DocumentReprocessing.Status.FAILED, "IllegalStateException"));
        assertThat(adapter.execute(migration)).isEqualTo(new ReprocessingDocumentExecutor.Result(
            ReprocessingDocumentExecutor.Status.FAILED, "IllegalStateException"));
    }

    @Test
    void mapsOutcomeScopeOptionalRevisionAndTimeWithoutAddingDecisions() throws Exception {
        AtomicReference<DocumentProcessingOutcomes.Request> captured = new AtomicReference<>();
        DocumentProcessingOutcomes documents = request -> { captured.set(request); return request.requiredChunkerRevision() != null; };
        ReprocessingProcessingOutcomeReader adapter = new ReprocessingProcessingOutcomeAdapter(documents);
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(adapter.completedOverwrite(new ReprocessingProcessingOutcomeReader.Request(
            "doc", "hash", "revision", started))).isTrue();
        assertThat(captured.get()).isEqualTo(new DocumentProcessingOutcomes.Request("doc", "hash", "revision", started));
        assertThat(adapter.completedOverwrite(new ReprocessingProcessingOutcomeReader.Request(
            "doc", "hash", null, null))).isFalse();
        assertThat(captured.get()).isEqualTo(new DocumentProcessingOutcomes.Request("doc", "hash", null, null));
    }
}
