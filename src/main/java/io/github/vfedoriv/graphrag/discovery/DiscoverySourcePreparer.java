package io.github.vfedoriv.graphrag.discovery;

import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.SourceType;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.ConceptRule;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.DiscoveryGuidance;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.PropertyRule;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.RelationshipRule;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DiscoverySourcePreparer {

    private final DocumentUploadRepository documentRepository;
    private final DocumentUploadService documentUploadService;
    private final DocumentParsingService parsingService;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final RuntimeSettingsService runtimeSettingsService;

    public DiscoverySourcePreparer(
        DocumentUploadRepository documentRepository,
        DocumentUploadService documentUploadService,
        DocumentParsingService parsingService,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        RuntimeSettingsService runtimeSettingsService
    ) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
        this.parsingService = parsingService;
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public List<PreparedDiscoverySource> prepare(
        String knowledgeBaseId, SchemaDiscoveryRequest request, List<MultipartFile> files
    ) {
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        validateGuidance(request.guidance());
        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        int sourceCount = request.documentIds().size() + request.textSources().size() + safeFiles.size();
        RuntimeSettingsService.DiscoverySettings limits = runtimeSettingsService.discovery();
        if (sourceCount < 1) {
            throw new IllegalArgumentException("At least one discovery source is required");
        }
        if (sourceCount > limits.maxSources()) {
            throw new IllegalArgumentException("Discovery source count exceeds limit " + limits.maxSources());
        }

        List<RawSource> rawSources = new ArrayList<>();
        long totalBytes = 0;
        int ordinal = 1;
        for (String documentId : request.documentIds()) {
            DocumentUploadNode document = ownedDocument(knowledgeBaseId, documentId);
            byte[] bytes = readDocument(document);
            totalBytes = addBytes(totalBytes, bytes.length, limits);
            String text = parsingService.parse(document.getOriginalFilename(), document.getContentType(), bytes);
            rawSources.add(new RawSource(ordinal++, SourceType.DOCUMENT, document.getOriginalFilename(), documentId, bytes, text));
        }
        for (SchemaDiscoveryRequest.TextSource textSource : request.textSources()) {
            byte[] bytes = textSource.text().getBytes(StandardCharsets.UTF_8);
            totalBytes = addBytes(totalBytes, bytes.length, limits);
            rawSources.add(new RawSource(ordinal++, SourceType.TEXT, textSource.name(), null, bytes, textSource.text()));
        }
        for (MultipartFile file : safeFiles) {
            byte[] bytes = readFile(file);
            totalBytes = addBytes(totalBytes, bytes.length, limits);
            String text = parsingService.parse(file.getOriginalFilename(), file.getContentType(), bytes);
            rawSources.add(new RawSource(ordinal++, SourceType.FILE, safeName(file), null, bytes, text));
        }

        int totalCharacters = rawSources.stream().mapToInt(source -> source.text().length()).sum();
        if (totalCharacters > limits.maxTotalCharacters()) {
            throw new IllegalArgumentException("Discovery parsed content exceeds total character limit " + limits.maxTotalCharacters());
        }
        return rawSources.stream().map(source -> prepareSource(source, limits)).toList();
    }

    private PreparedDiscoverySource prepareSource(RawSource source, RuntimeSettingsService.DiscoverySettings limits) {
        String text = source.text() == null ? "" : source.text().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Discovery source " + source.ordinal() + " has no parseable content");
        }
        if (text.length() > limits.maxSourceCharacters()) {
            throw new IllegalArgumentException("Discovery source " + source.ordinal() + " exceeds character limit " + limits.maxSourceCharacters());
        }
        String fingerprint = sha256(source.bytes());
        String sourceId = String.format(Locale.ROOT, "source-%03d-%s-%s", source.ordinal(),
            source.type().name().toLowerCase(Locale.ROOT), fingerprint.substring(0, 12));
        List<PreparedDiscoverySource.AnalysisChunk> chunks = new ArrayList<>();
        for (int start = 0, chunkOrdinal = 1; start < text.length(); start += limits.chunkCharacters(), chunkOrdinal++) {
            if (chunkOrdinal > limits.maxChunksPerSource()) {
                throw new IllegalArgumentException("Discovery source " + source.ordinal() + " exceeds chunk limit " + limits.maxChunksPerSource());
            }
            int end = Math.min(text.length(), start + limits.chunkCharacters());
            String chunkText = text.substring(start, end);
            String chunkId = sourceId + "-chunk-" + String.format(Locale.ROOT, "%03d", chunkOrdinal);
            chunks.add(new PreparedDiscoverySource.AnalysisChunk(
                chunkId, chunkOrdinal, chunkText, sha256(chunkText.getBytes(StandardCharsets.UTF_8))
            ));
        }
        return new PreparedDiscoverySource(sourceId, source.type(), source.name(), source.documentId(), fingerprint, chunks);
    }

    private DocumentUploadNode ownedDocument(String knowledgeBaseId, String documentId) {
        DocumentUploadNode document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found in knowledge base: " + documentId));
        if (!knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            throw new NotFoundException("Document not found in knowledge base: " + documentId);
        }
        if (document.getContentUri() == null || document.getContentUri().isBlank()) {
            throw new IllegalArgumentException("Document content is unavailable: " + documentId);
        }
        return document;
    }

    private byte[] readDocument(DocumentUploadNode document) {
        try {
            return documentUploadService.readContent(document.getContentUri());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Document content cannot be read: " + document.getId(), exception);
        }
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Discovery file must not be empty");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Discovery file cannot be read", exception);
        }
    }

    private long addBytes(long total, int sourceBytes, RuntimeSettingsService.DiscoverySettings limits) {
        if (sourceBytes > limits.maxSourceBytes()) {
            throw new IllegalArgumentException("Discovery source exceeds byte limit " + limits.maxSourceBytes());
        }
        long updated = total + sourceBytes;
        if (updated > limits.maxTotalBytes()) {
            throw new IllegalArgumentException("Discovery request exceeds total byte limit " + limits.maxTotalBytes());
        }
        return updated;
    }

    private void validateGuidance(DiscoveryGuidance guidance) {
        Set<String> required = conceptNames(guidance.requiredConcepts(), "requiredConcepts");
        Set<String> preferred = conceptNames(guidance.preferredConcepts(), "preferredConcepts");
        Set<String> excluded = normalizedSet(guidance.excludedConcepts(), "excludedConcepts");
        rejectOverlap(required, preferred, "required and preferred");
        rejectOverlap(required, excluded, "required and excluded");
        rejectOverlap(preferred, excluded, "preferred and excluded");
        Set<String> known = new HashSet<>();
        known.addAll(required);
        known.addAll(preferred);
        for (PropertyRule rule : guidance.propertyRules()) {
            if (!known.contains(key(rule.owner()))) {
                throw new IllegalArgumentException("guidance.propertyRules owner is not a required or preferred concept: " + rule.owner());
            }
        }
        Set<String> relationships = new HashSet<>();
        for (RelationshipRule rule : guidance.relationshipRules()) {
            if (!relationships.add(key(rule.type()) + ":" + key(rule.from()) + ":" + key(rule.to()))) {
                throw new IllegalArgumentException("guidance.relationshipRules contains a duplicate rule: " + rule.type());
            }
            if (!known.contains(key(rule.from())) || !known.contains(key(rule.to()))) {
                throw new IllegalArgumentException("guidance.relationshipRules endpoints must be required or preferred concepts: " + rule.type());
            }
        }
    }

    private Set<String> conceptNames(List<ConceptRule> rules, String field) {
        List<String> values = rules.stream().map(ConceptRule::name).toList();
        return normalizedSet(values, field);
    }

    private Set<String> normalizedSet(List<String> values, String field) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (!result.add(key(value))) {
                throw new IllegalArgumentException("guidance." + field + " contains duplicate concept: " + value);
            }
        }
        return result;
    }

    private void rejectOverlap(Set<String> left, Set<String> right, String description) {
        Set<String> overlap = new HashSet<>(left);
        overlap.retainAll(right);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("guidance contains contradictory " + description + " concepts: " + overlap);
        }
    }

    private String key(String value) {
        return DiscoveryContracts.normalizeLabel(value).toLowerCase(Locale.ROOT);
    }

    private String safeName(MultipartFile file) {
        return file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private record RawSource(int ordinal, SourceType type, String name, String documentId, byte[] bytes, String text) {
    }
}
