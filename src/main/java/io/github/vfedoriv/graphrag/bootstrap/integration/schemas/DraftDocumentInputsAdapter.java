package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentSourceInputs;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftDocumentInputs;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DraftDocumentInputsAdapter implements DraftDocumentInputs {
    private final DocumentSourceInputs inputs;

    public DraftDocumentInputsAdapter(DocumentSourceInputs inputs) {
        this.inputs = inputs;
    }

    @Override
    public Optional<Metadata> inspectOwned(String knowledgeBaseId, String documentId) {
        return inputs.inspectOwned(knowledgeBaseId, documentId).map(value -> new Metadata(
            value.documentId(), value.filename(), value.contentType(), value.sizeBytes(), value.sha256()));
    }

    @Override
    public byte[] readOwned(String knowledgeBaseId, String documentId) {
        return inputs.readOwned(knowledgeBaseId, documentId).bytes();
    }

    @Override
    public String parse(String filename, String contentType, byte[] bytes) {
        return inputs.parse(filename, contentType, bytes);
    }
}
