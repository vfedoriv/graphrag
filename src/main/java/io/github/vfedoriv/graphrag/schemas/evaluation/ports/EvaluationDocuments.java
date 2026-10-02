package io.github.vfedoriv.graphrag.schemas.evaluation.ports;
import java.time.Instant;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
public interface EvaluationDocuments {
    DocumentPage listOwned(String knowledgeBaseId, int page, int size);
    Optional<Metadata> inspectOwned(String knowledgeBaseId, String documentId);
    Prepared prepareOwned(String knowledgeBaseId, String documentId) throws IOException;
    Optional<Source> captureOwned(String knowledgeBaseId, String documentId);
    interface Source {
        Metadata metadata();
        Prepared prepare() throws IOException;
    }
    record DocumentPage(int page, int size, long totalElements, int totalPages, List<Metadata> documents) {
        public DocumentPage { documents = List.copyOf(documents); }
    }
    record Metadata(String documentId, String filename, String contentType, long sizeBytes, String sha256, Instant uploadedAt) { }
    record Prepared(String documentId, List<String> chunks) {
        public Prepared { chunks = List.copyOf(chunks); }
    }
}
