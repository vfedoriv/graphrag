package io.github.vfedoriv.graphrag.documents.application.inspection;

import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentParsingService;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentSourceInputs;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DocumentSourceInputsFacade implements DocumentSourceInputs {
    private final DocumentUploadRepository documents;
    private final DocumentUploadService binaries;
    private final DocumentParsingService parsing;

    public DocumentSourceInputsFacade(
        DocumentUploadRepository documents,
        DocumentUploadService binaries,
        DocumentParsingService parsing
    ) {
        this.documents = documents;
        this.binaries = binaries;
        this.parsing = parsing;
    }

    @Override
    public Optional<Metadata> inspectOwned(String knowledgeBaseId, String documentId) {
        return documents.findById(documentId)
            .filter(document -> knowledgeBaseId.equals(document.getKnowledgeBaseId()))
            .map(document -> new Metadata(document.getId(), document.getOriginalFilename(), document.getContentType(),
                document.getSizeBytes(), document.getSha256()));
    }

    @Override
    public Source readOwned(String knowledgeBaseId, String documentId) {
        DocumentUploadNode document = documents.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found in knowledge base: " + documentId));
        if (!knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            throw new NotFoundException("Document not found in knowledge base: " + documentId);
        }
        if (document.getContentUri() == null || document.getContentUri().isBlank()) {
            throw new IllegalArgumentException("Document content is unavailable: " + documentId);
        }
        try {
            return new Source(documentId, document.getOriginalFilename(), document.getContentType(),
                binaries.readContent(document.getContentUri()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Document content cannot be read: " + documentId, exception);
        }
    }

    @Override
    public String parse(String filename, String contentType, byte[] bytes) {
        return parsing.parse(filename, contentType, bytes);
    }
}
