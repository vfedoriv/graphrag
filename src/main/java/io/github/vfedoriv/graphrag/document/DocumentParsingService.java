package io.github.vfedoriv.graphrag.document;

import java.io.ByteArrayInputStream;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DocumentParsingService {

    private final RoutedDocumentParser routedDocumentParser;

    public DocumentParsingService(RoutedDocumentParser routedDocumentParser) {
        this.routedDocumentParser = routedDocumentParser;
    }

    public String parse(String filename, String contentType, byte[] bytes) {
        return parseStructured(filename, contentType, bytes, Map.of()).text();
    }

    public ParsedDocument parseStructured(
        String filename,
        String contentType,
        byte[] bytes,
        Map<String, Object> processingOptions
    ) {
        return routedDocumentParser.parse(filename, contentType, new ByteArrayInputStream(bytes), processingOptions);
    }
}
