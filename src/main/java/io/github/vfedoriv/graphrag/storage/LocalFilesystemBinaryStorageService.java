package io.github.vfedoriv.graphrag.storage;

import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LocalFilesystemBinaryStorageService implements BinaryStorageService {

    private final RuntimeSettingsService runtimeSettingsService;

    public LocalFilesystemBinaryStorageService(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @Override
    public URI store(String knowledgeBaseId, String documentId, String originalFilename, byte[] bytes) throws IOException {
        Path kbPath = documentsRoot().resolve(knowledgeBaseId).normalize();
        Files.createDirectories(kbPath);

        String sanitizedFilename = sanitizeFilename(originalFilename);
        Path target = kbPath.resolve(documentId + "-" + sanitizedFilename).normalize();
        log.info(
            "Storing document bytes: knowledgeBaseId={}, documentId={}, originalFilename={}, target={}, bytes={}",
            knowledgeBaseId,
            documentId,
            originalFilename,
            target,
            bytes == null ? 0 : bytes.length
        );
        Files.write(target, bytes);
        URI uri = target.toUri();
        log.info("Document bytes stored: knowledgeBaseId={}, documentId={}, uri={}", knowledgeBaseId, documentId, uri);
        return uri;
    }

    @Override
    public URI storeDraftSource(
        String knowledgeBaseId, String draftId, String sourceId, String originalFilename, byte[] bytes
    ) throws IOException {
        Path draftPath = documentsRoot().resolve("drafts").resolve(knowledgeBaseId).resolve(draftId).normalize();
        Files.createDirectories(draftPath);
        String sanitizedFilename = sanitizeFilename(originalFilename);
        Path target = draftPath.resolve(sourceId + "-" + sanitizedFilename).normalize();
        if (!target.startsWith(draftPath)) {
            throw new IOException("Invalid draft source storage path");
        }
        Files.write(target, bytes);
        log.info(
            "Draft source bytes stored: knowledgeBaseId={}, draftId={}, sourceId={}, bytes={}",
            knowledgeBaseId,
            draftId,
            sourceId,
            bytes == null ? 0 : bytes.length
        );
        return target.toUri();
    }

    @Override
    public InputStream read(URI contentUri) throws IOException {
        Path path = resolvePath(contentUri);
        log.info("Opening document content stream: uri={}, path={}", contentUri, path);
        return Files.newInputStream(path);
    }

    @Override
    public Path resolvePath(URI contentUri) {
        return Path.of(contentUri).toAbsolutePath().normalize();
    }

    @Override
    public void delete(URI contentUri) throws IOException {
        Path path = resolvePath(contentUri);
        log.info("Deleting document content from storage: uri={}, path={}", contentUri, path);
        Files.delete(path);
        log.info("Document content deleted from storage: uri={}, path={}", contentUri, path);
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document.bin";
        }
        return originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Path documentsRoot() {
        return runtimeSettingsService.documentStorageRoot().toAbsolutePath().normalize();
    }
}
