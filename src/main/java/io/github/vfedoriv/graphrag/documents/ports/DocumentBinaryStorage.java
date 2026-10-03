package io.github.vfedoriv.graphrag.documents.ports;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/** Binary effects for document workflows; shared draft storage remains support-owned. */
public interface DocumentBinaryStorage {
    URI store(String knowledgeBaseId, String documentId, String originalFilename, byte[] bytes) throws IOException;
    InputStream read(URI contentUri) throws IOException;
    String localPath(URI contentUri);
    void delete(URI contentUri) throws IOException;
    void deleteIfPresent(URI contentUri) throws IOException;
}
