package io.github.vfedoriv.graphrag.documents.contracts;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only document inputs used by schema evaluation.
 */
public interface DocumentEvaluationPreparation {

    DocumentPage listOwned(String knowledgeBaseId, int page, int size);

    Optional<DocumentMetadata> inspectOwned(String knowledgeBaseId, String documentId);

    Optional<Source> captureOwned(String knowledgeBaseId, String documentId);

    PreparedDocument prepareOwned(String knowledgeBaseId, String documentId) throws IOException;

    interface Source {
        DocumentMetadata metadata();

        PreparedDocument prepare() throws IOException;
    }

    record DocumentPage(int page, int size, long totalElements, int totalPages, List<DocumentMetadata> documents) {
        public DocumentPage {
            documents = List.copyOf(documents);
        }
    }

    record DocumentMetadata(
        String documentId,
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant uploadedAt
    ) { }

    record PreparedDocument(String documentId, List<String> chunks) {
        public PreparedDocument {
            chunks = List.copyOf(chunks);
        }
    }
}
