package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SourceParsingStage {

    private final DocumentUploadService documentUploadService;
    private final DocumentParsingService documentParsingService;

    public SourceParsingStage(DocumentUploadService documentUploadService, DocumentParsingService documentParsingService) {
        this.documentUploadService = documentUploadService;
        this.documentParsingService = documentParsingService;
    }

    public ParsedDocument parse(DocumentUploadNode document, Map<String, Object> effectiveOptions) throws IOException {
        byte[] bytes = documentUploadService.readContent(document.getContentUri());
        log.info("Loaded document bytes from storage: documentId={}, bytes={}", document.getId(), bytes.length);
        return documentParsingService.parseStructured(
            document.getOriginalFilename(),
            document.getContentType(),
            bytes,
            effectiveOptions
        );
    }
}
