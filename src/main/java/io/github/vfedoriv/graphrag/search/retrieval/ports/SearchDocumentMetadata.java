package io.github.vfedoriv.graphrag.search.retrieval.ports;
import java.util.List;
public interface SearchDocumentMetadata {
    List<Metadata> findOwnedBatch(String knowledgeBaseId, List<String> documentIds);
    List<String> selectOwned(String knowledgeBaseId, String filename, String contentType, int limit);
    record Metadata(String documentId, String filename, String contentType) { }
}
