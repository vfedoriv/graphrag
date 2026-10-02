package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentSourceInputs;
import io.github.vfedoriv.graphrag.schemas.discovery.ports.DiscoveryDocumentInputs;
import io.github.vfedoriv.graphrag.schemas.discovery.ports.DiscoveryFileParsing;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryDocumentInputsAdapter implements DiscoveryDocumentInputs, DiscoveryFileParsing {
    private final DocumentSourceInputs documents;

    public DiscoveryDocumentInputsAdapter(DocumentSourceInputs documents) {
        this.documents = documents;
    }

    @Override
    public DiscoveryDocumentInputs.Source readOwned(String knowledgeBaseId, String documentId) {
        DocumentSourceInputs.Source source = documents.readOwned(knowledgeBaseId, documentId);
        return new DiscoveryDocumentInputs.Source(
            source.documentId(), source.filename(), source.contentType(), source.bytes());
    }

    @Override
    public String parse(String filename, String contentType, byte[] bytes) {
        return documents.parse(filename, contentType, bytes);
    }
}
