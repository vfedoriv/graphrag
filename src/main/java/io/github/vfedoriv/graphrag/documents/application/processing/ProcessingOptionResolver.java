package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentFormatDetection;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionSet;
import java.util.Map;

public final class ProcessingOptionResolver {

    private final DocumentProcessingOptionsRegistry registry;
    private final ProcessingJsonCodec jsonCodec;

    public ProcessingOptionResolver(DocumentProcessingOptionsRegistry registry, ProcessingJsonCodec jsonCodec) {
        this.registry = registry;
        this.jsonCodec = jsonCodec;
    }

    public DocumentProcessingOptionSet resolve(DocumentUploadNode document, Map<String, Object> requestedOptions) {
        DocumentFormatDetection detection = registry.detect(document.getOriginalFilename(), document.getContentType());
        Map<String, Object> savedDefaults = savedDefaults(document, detection);
        Map<String, Object> requested = registry.validate(detection, requestedOptions);
        Map<String, Object> effective = registry.merge(detection, savedDefaults, requested);
        return new DocumentProcessingOptionSet(detection, requested, savedDefaults, effective);
    }

    public Map<String, Object> savedDefaults(DocumentUploadNode document, DocumentFormatDetection detection) {
        return registry.validate(detection, jsonCodec.readMap(document.getProcessingDefaultsJson()));
    }
}
