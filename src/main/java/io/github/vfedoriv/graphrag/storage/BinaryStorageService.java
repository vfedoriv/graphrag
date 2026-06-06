package io.github.vfedoriv.graphrag.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;

public interface BinaryStorageService {

    URI store(String knowledgeBaseId, String documentId, String originalFilename, byte[] bytes) throws IOException;

    InputStream read(URI contentUri) throws IOException;

    Path resolvePath(URI contentUri);

    void delete(URI contentUri) throws IOException;
}
