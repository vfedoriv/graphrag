package io.github.vfedoriv.graphrag.storage;

import io.github.vfedoriv.graphrag.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class LocalFilesystemBinaryStorageService implements BinaryStorageService {

    private final Path documentsRoot;

    public LocalFilesystemBinaryStorageService(AppProperties appProperties) {
        this.documentsRoot = appProperties.storage().documentsRoot().toAbsolutePath().normalize();
    }

    @Override
    public URI store(String knowledgeBaseId, String documentId, String originalFilename, byte[] bytes) throws IOException {
        Path kbPath = documentsRoot.resolve(knowledgeBaseId).normalize();
        Files.createDirectories(kbPath);

        String sanitizedFilename = sanitizeFilename(originalFilename);
        Path target = kbPath.resolve(documentId + "-" + sanitizedFilename).normalize();
        Files.write(target, bytes);
        return target.toUri();
    }

    @Override
    public InputStream read(URI contentUri) throws IOException {
        return Files.newInputStream(resolvePath(contentUri));
    }

    @Override
    public Path resolvePath(URI contentUri) {
        return Path.of(contentUri).toAbsolutePath().normalize();
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document.bin";
        }
        return originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
