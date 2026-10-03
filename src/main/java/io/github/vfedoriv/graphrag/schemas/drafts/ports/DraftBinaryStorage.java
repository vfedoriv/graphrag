package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public interface DraftBinaryStorage {
    URI storeDraftSource(
        String knowledgeBaseId, String draftId, String sourceId, String filename, byte[] bytes
    ) throws IOException;

    InputStream read(URI uri) throws IOException;

    boolean exists(URI uri);

    void delete(URI uri) throws IOException;
}
