package io.github.vfedoriv.graphrag.documents.adapters.binary;

import io.github.vfedoriv.graphrag.documents.ports.DocumentBinaryStorage;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import org.springframework.stereotype.Component;

@Component
public class DocumentBinaryStorageAdapter implements DocumentBinaryStorage {
    private final BinaryStorageService storage;

    public DocumentBinaryStorageAdapter(BinaryStorageService storage) {
        this.storage = storage;
    }

    @Override
    public URI store(String knowledgeBaseId, String documentId, String originalFilename, byte[] bytes) throws IOException {
        return storage.store(knowledgeBaseId, documentId, originalFilename, bytes);
    }

    @Override
    public InputStream read(URI contentUri) throws IOException {
        return storage.read(contentUri);
    }

    @Override
    public String localPath(URI contentUri) {
        return storage.resolvePath(contentUri).toString();
    }

    @Override
    public void delete(URI contentUri) throws IOException {
        storage.delete(contentUri);
    }

    @Override
    public void deleteIfPresent(URI contentUri) throws IOException {
        if (!Files.notExists(storage.resolvePath(contentUri))) {
            storage.delete(contentUri);
        }
    }
}
