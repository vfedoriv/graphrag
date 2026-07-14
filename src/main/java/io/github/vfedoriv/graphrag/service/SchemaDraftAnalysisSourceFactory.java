package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.SourceType;
import io.github.vfedoriv.graphrag.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SchemaDraftAnalysisSourceFactory {
    private final DocumentUploadRepository documentRepository;
    private final DocumentUploadService documentUploadService;
    private final BinaryStorageService storageService;
    private final DocumentParsingService parsingService;
    private final RuntimeSettingsService runtimeSettingsService;

    public SchemaDraftAnalysisSourceFactory(
        DocumentUploadRepository documentRepository,
        DocumentUploadService documentUploadService,
        BinaryStorageService storageService,
        DocumentParsingService parsingService,
        RuntimeSettingsService runtimeSettingsService
    ) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
        this.storageService = storageService;
        this.parsingService = parsingService;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public PreparedDiscoverySource prepare(SchemaDraftSourceNode source) {
        byte[] bytes = bytes(source);
        String text = source.getType() == SchemaDraftSourceType.TEXT
            ? new String(bytes, StandardCharsets.UTF_8)
            : parsingService.parse(source.getName(), source.getContentType(), bytes);
        text = text == null ? "" : text.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Draft source has no parseable content");
        }
        RuntimeSettingsService.DiscoverySettings settings = runtimeSettingsService.discovery();
        if (text.length() > settings.maxSourceCharacters()) {
            throw new IllegalArgumentException("Draft source exceeds parsed character limit");
        }
        List<PreparedDiscoverySource.AnalysisChunk> chunks = new ArrayList<>();
        for (int start = 0, ordinal = 1; start < text.length(); start += settings.chunkCharacters(), ordinal++) {
            if (ordinal > settings.maxChunksPerSource()) {
                throw new IllegalArgumentException("Draft source exceeds chunk limit");
            }
            int end = Math.min(text.length(), start + settings.chunkCharacters());
            String chunkText = text.substring(start, end);
            String chunkId = source.getId() + "-r" + source.getRevision() + "-chunk-"
                + String.format(Locale.ROOT, "%03d", ordinal);
            chunks.add(new PreparedDiscoverySource.AnalysisChunk(
                chunkId, ordinal, chunkText, Integer.toHexString(chunkText.hashCode())));
        }
        return new PreparedDiscoverySource(source.getId(), sourceType(source.getType()), source.getName(),
            source.getDocumentId(), source.getSha256(), chunks);
    }

    private byte[] bytes(SchemaDraftSourceNode source) {
        try {
            if (source.getType() == SchemaDraftSourceType.DOCUMENT) {
                DocumentUploadNode document = documentRepository.findById(source.getDocumentId()).orElse(null);
                if (document == null) {
                    source.setStatus(SchemaDraftSourceStatus.UNAVAILABLE);
                    throw new IllegalArgumentException("Referenced document is unavailable");
                }
                if (!source.getSha256().equals(document.getSha256())) {
                    source.setStatus(SchemaDraftSourceStatus.STALE);
                    throw new IllegalArgumentException("Referenced document is stale");
                }
                return documentUploadService.readContent(document.getContentUri());
            }
            try (InputStream input = storageService.read(URI.create(source.getContentUri()))) {
                return input.readAllBytes();
            }
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Draft source content is unavailable", exception);
        }
    }

    private SourceType sourceType(SchemaDraftSourceType type) {
        return switch (type) {
            case DOCUMENT -> SourceType.DOCUMENT;
            case FILE -> SourceType.FILE;
            case TEXT -> SourceType.TEXT;
        };
    }
}
