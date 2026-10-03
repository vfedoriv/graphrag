package io.github.vfedoriv.graphrag.schemas.drafts.adapters.binary;

import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftBinaryStorage;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import org.springframework.stereotype.Component;

@Component
public class DraftBinaryStorageAdapter implements DraftBinaryStorage {
    private final BinaryStorageService storage;

    public DraftBinaryStorageAdapter(BinaryStorageService storage) {
        this.storage = storage;
    }

    @Override
    public URI storeDraftSource(
        String knowledgeBaseId, String draftId, String sourceId, String filename, byte[] bytes
    ) throws IOException {
        return storage.storeDraftSource(knowledgeBaseId, draftId, sourceId, filename, bytes);
    }

    @Override
    public InputStream read(URI uri) throws IOException {
        return storage.read(uri);
    }

    @Override
    public boolean exists(URI uri) {
        return Files.exists(storage.resolvePath(uri));
    }

    @Override
    public void delete(URI uri) throws IOException {
        storage.delete(uri);
    }
}
