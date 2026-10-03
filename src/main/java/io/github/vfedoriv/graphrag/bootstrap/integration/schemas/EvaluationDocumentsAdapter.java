package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentEvaluationPreparation;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationDocuments;
import java.util.Optional;
import java.io.IOException;
import org.springframework.stereotype.Component;
@Component
public class EvaluationDocumentsAdapter implements EvaluationDocuments {
    private final DocumentEvaluationPreparation documents;
    public EvaluationDocumentsAdapter(DocumentEvaluationPreparation documents) { this.documents = documents; }
    @Override public DocumentPage listOwned(String knowledgeBaseId, int page, int size) {
        DocumentEvaluationPreparation.DocumentPage value = documents.listOwned(knowledgeBaseId, page, size);
        return new DocumentPage(value.page(), value.size(), value.totalElements(), value.totalPages(),
            value.documents().stream().map(this::map).toList());
    }
    @Override public Optional<Metadata> inspectOwned(String knowledgeBaseId, String documentId) {
        return documents.inspectOwned(knowledgeBaseId, documentId).map(this::map);
    }
    @Override public Prepared prepareOwned(String knowledgeBaseId, String documentId) throws IOException {
        DocumentEvaluationPreparation.PreparedDocument value = documents.prepareOwned(knowledgeBaseId, documentId);
        return new Prepared(value.documentId(), value.chunks());
    }
    @Override public Optional<Source> captureOwned(String knowledgeBaseId, String documentId) {
        return documents.captureOwned(knowledgeBaseId, documentId).map(value -> new Source() {
            @Override public Metadata metadata() { return map(value.metadata()); }
            @Override public Prepared prepare() throws IOException {
                DocumentEvaluationPreparation.PreparedDocument prepared = value.prepare();
                return new Prepared(prepared.documentId(), prepared.chunks());
            }
        });
    }
    private Metadata map(DocumentEvaluationPreparation.DocumentMetadata value) {
        return new Metadata(value.documentId(), value.filename(), value.contentType(), value.sizeBytes(), value.sha256(), value.uploadedAt());
    }
}
