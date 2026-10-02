package io.github.vfedoriv.graphrag.bootstrap.integration.search;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentMetadataAccess;
import io.github.vfedoriv.graphrag.search.retrieval.ports.SearchDocumentMetadata;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class SearchDocumentMetadataAdapter implements SearchDocumentMetadata {
    private final DocumentMetadataAccess documents;
    public SearchDocumentMetadataAdapter(DocumentMetadataAccess documents) { this.documents = documents; }
    public List<Metadata> findOwnedBatch(String knowledgeBaseId, List<String> documentIds) {
        return documents.findOwnedBatch(knowledgeBaseId, documentIds).stream()
            .map(value -> new Metadata(value.documentId(), value.filename(), value.contentType())).toList();
    }
    public List<String> selectOwned(String knowledgeBaseId, String filename, String contentType, int limit) {
        return documents.selectOwned(knowledgeBaseId, filename, contentType, limit);
    }
}
