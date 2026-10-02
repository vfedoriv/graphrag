package io.github.vfedoriv.graphrag.schemas.drafts.application;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.DraftSourceFingerprint;

import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;

import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftDocumentInputs;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceType;
import io.github.vfedoriv.graphrag.schemas.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftBinaryStorage;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SchemaDraftAnalysisSourceFactory {
    private final DraftDocumentInputs documentRepository;
    private final DraftBinaryStorage storageService;
    private final RuntimeSettingsService runtimeSettingsService;

    public SchemaDraftAnalysisSourceFactory(
        DraftDocumentInputs documentRepository,
        DraftBinaryStorage storageService,
        RuntimeSettingsService runtimeSettingsService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public PreparedDiscoverySource prepare(String knowledgeBaseId, SchemaDraftSourceNode source) {
        byte[] bytes = bytes(knowledgeBaseId, source);
        String text = source.getType() == SchemaDraftSourceType.TEXT
            ? new String(bytes, StandardCharsets.UTF_8)
            : documentRepository.parse(source.getName(), source.getContentType(), bytes);
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

    private byte[] bytes(String knowledgeBaseId, SchemaDraftSourceNode source) {
        try {
            if (source.getType() == SchemaDraftSourceType.DOCUMENT) {
                DraftDocumentInputs.Metadata document = documentRepository.inspectOwned(knowledgeBaseId, source.getDocumentId()).orElse(null);
                if (document == null) {
                    source.setStatus(SchemaDraftSourceStatus.UNAVAILABLE);
                    throw new IllegalArgumentException("Referenced document is unavailable");
                }
                if (!source.getSha256().equals(document.sha256())) {
                    source.setStatus(SchemaDraftSourceStatus.STALE);
                    throw new IllegalArgumentException("Referenced document is stale");
                }
                byte[] content;
                try {
                    content = documentRepository.readOwned(knowledgeBaseId, source.getDocumentId());
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException("Draft source content is unavailable", exception);
                }
                if (!source.getSha256().equals(DraftSourceFingerprint.sha256(content))) {
                    source.setStatus(SchemaDraftSourceStatus.STALE);
                    throw new IllegalArgumentException("Referenced document is stale");
                }
                return content;
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
