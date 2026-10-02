package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import java.util.Optional;

public interface DraftDocumentInputs {
    Optional<Metadata> inspectOwned(String knowledgeBaseId, String documentId);
    byte[] readOwned(String knowledgeBaseId, String documentId);
    String parse(String filename, String contentType, byte[] bytes);

    record Metadata(String documentId, String filename, String contentType, long sizeBytes, String sha256) { }
}
