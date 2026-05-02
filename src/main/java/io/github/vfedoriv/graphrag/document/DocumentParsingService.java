package io.github.vfedoriv.graphrag.document;

import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Service;

@Service
public class DocumentParsingService {

    private final RoutedDocumentParser routedDocumentParser;

    public DocumentParsingService(RoutedDocumentParser routedDocumentParser) {
        this.routedDocumentParser = routedDocumentParser;
    }

    public String parse(String filename, String contentType, byte[] bytes) {
        return routedDocumentParser.parserFor(filename, contentType).parse(new ByteArrayInputStream(bytes)).text();
    }
}
