package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.service.DocumentFormatDetection;
import io.github.vfedoriv.graphrag.service.DocumentProcessingOptionSet;
import io.github.vfedoriv.graphrag.service.DocumentProcessingOptionsRegistry;
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
