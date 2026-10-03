package io.github.vfedoriv.graphrag.documents.application.inspection;

import io.github.vfedoriv.graphrag.documents.application.processing.ChunkingService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentParsingService;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentEvaluationPreparation;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import java.io.IOException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DocumentEvaluationPreparationFacade implements DocumentEvaluationPreparation {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentUploadRepository documents;
    private final DocumentUploadService binaries;
    private final DocumentParsingService parsing;
    private final ChunkingService chunking;

    public DocumentEvaluationPreparationFacade(
        DocumentUploadRepository documents,
        DocumentUploadService binaries,
        DocumentParsingService parsing,
        ChunkingService chunking
    ) {
        this.documents = documents;
        this.binaries = binaries;
        this.parsing = parsing;
        this.chunking = chunking;
    }

    @Override
    public DocumentPage listOwned(String knowledgeBaseId, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        Page<DocumentUploadNode> documentsPage = documents.findPageByKnowledgeBaseId(
            knowledgeBaseId, PageRequest.of(boundedPage, boundedSize));
        return new DocumentPage(
            boundedPage,
            boundedSize,
            documentsPage.getTotalElements(),
            documentsPage.getTotalPages(),
            documentsPage.getContent().stream().map(this::metadata).toList()
        );
    }

    @Override
    public Optional<DocumentMetadata> inspectOwned(String knowledgeBaseId, String documentId) {
        return documents.findByIdAndKnowledgeBaseId(documentId, knowledgeBaseId).map(this::metadata);
    }

    @Override
    public Optional<Source> captureOwned(String knowledgeBaseId, String documentId) {
        return documents.findByIdAndKnowledgeBaseId(documentId, knowledgeBaseId)
            .map(this::capture);
    }

    @Override
    public PreparedDocument prepareOwned(String knowledgeBaseId, String documentId) throws IOException {
        Source source = captureOwned(knowledgeBaseId, documentId)
            .orElseThrow(() -> new NotFoundException("Document not found in knowledge base: " + documentId));
        return source.prepare();
    }

    private Source capture(DocumentUploadNode document) {
        DocumentMetadata metadata = metadata(document);
        return new CapturedSource(
            metadata,
            document.getContentUri(),
            binaries,
            parsing,
            chunking
        );
    }

    private DocumentMetadata metadata(DocumentUploadNode document) {
        return new DocumentMetadata(
            document.getId(),
            document.getOriginalFilename(),
            document.getContentType(),
            document.getSizeBytes(),
            document.getSha256(),
            document.getUploadedAt()
        );
    }

    private static final class CapturedSource implements Source {

        private final DocumentMetadata metadata;
        private final String contentUri;
        private final DocumentUploadService binaries;
        private final DocumentParsingService parsing;
        private final ChunkingService chunking;

        private CapturedSource(
            DocumentMetadata metadata,
            String contentUri,
            DocumentUploadService binaries,
            DocumentParsingService parsing,
            ChunkingService chunking
        ) {
            this.metadata = metadata;
            this.contentUri = contentUri;
            this.binaries = binaries;
            this.parsing = parsing;
            this.chunking = chunking;
        }

        @Override
        public DocumentMetadata metadata() {
            return metadata;
        }

        @Override
        public PreparedDocument prepare() throws IOException {
            byte[] bytes = binaries.readContent(contentUri);
            String text = parsing.parse(metadata.filename(), metadata.contentType(), bytes);
            return new PreparedDocument(metadata.documentId(), chunking.split(text));
        }
    }
}
