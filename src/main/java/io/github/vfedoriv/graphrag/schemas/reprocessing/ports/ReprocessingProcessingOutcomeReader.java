package io.github.vfedoriv.graphrag.schemas.reprocessing.ports;

import java.time.Instant;
import java.util.Objects;

/** Inspects the active completed overwrite using the historical recovery predicate. */
public interface ReprocessingProcessingOutcomeReader {
    boolean completedOverwrite(Request request);

    record Request(String documentId, String expectedSourceSha256,
                   String requiredChunkerRevision, Instant itemStartedAt) {
        public Request {
            Objects.requireNonNull(documentId);
            Objects.requireNonNull(expectedSourceSha256);
        }
    }
}
