package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.SourceResponse;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.infrastructure.persistence.SchemaDraftGraphService;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRevisionRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class SchemaDraftSourceService {
    private final SchemaDraftLifecycleService lifecycleService;
    private final SchemaDraftRepository draftRepository;
    private final SchemaDraftSourceRepository sourceRepository;
    private final SchemaDraftSourceRevisionRepository revisionRepository;
    private final DocumentUploadRepository documentRepository;
    private final BinaryStorageService storageService;
    private final SchemaDraftStorageMutationService mutationService;
    private final SchemaDraftGraphService graphService;
    private final RuntimeSettingsService runtimeSettingsService;

    public SchemaDraftSourceService(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftRepository draftRepository,
        SchemaDraftSourceRepository sourceRepository,
        SchemaDraftSourceRevisionRepository revisionRepository,
        DocumentUploadRepository documentRepository,
        BinaryStorageService storageService,
        SchemaDraftStorageMutationService mutationService,
        SchemaDraftGraphService graphService,
        RuntimeSettingsService runtimeSettingsService
    ) {
        this.lifecycleService = lifecycleService;
        this.draftRepository = draftRepository;
        this.sourceRepository = sourceRepository;
        this.revisionRepository = revisionRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.mutationService = mutationService;
        this.graphService = graphService;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @Transactional
    public SourceResponse addDocument(String knowledgeBaseId, String draftId, long draftRevision, String documentId) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        DocumentUploadNode document = requireDocument(knowledgeBaseId, documentId);
        return existing(draftId, SchemaDraftSourceType.DOCUMENT, document.getSha256())
            .map(this::toResponse)
            .orElseGet(() -> create(draft, null, SchemaDraftSourceType.DOCUMENT, document.getOriginalFilename(),
                document.getContentType(), document.getSizeBytes(), document.getSha256(), document.getId(), null));
    }

    public SourceResponse addText(String knowledgeBaseId, String draftId, long draftRevision, String name, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Draft text source must not be blank");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        validateBytes(bytes, text.length());
        return addOwned(knowledgeBaseId, draftId, draftRevision, SchemaDraftSourceType.TEXT, name, "text/plain", bytes);
    }

    public SourceResponse addFile(
        String knowledgeBaseId, String draftId, long draftRevision, MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Draft source file must not be empty");
        }
        try {
            byte[] bytes = file.getBytes();
            validateBytes(bytes, 0);
            return addOwned(knowledgeBaseId, draftId, draftRevision, SchemaDraftSourceType.FILE,
                safeName(file.getOriginalFilename()), file.getContentType(), bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Draft source file cannot be read", exception);
        }
    }

    @Transactional
    public List<SourceResponse> list(String knowledgeBaseId, String draftId) {
        lifecycleService.requireOwned(knowledgeBaseId, draftId);
        List<SchemaDraftSourceNode> sources = sourceRepository.findByDraftIdOrderByCreatedAtAsc(draftId);
        sources.stream().filter(source -> source.getType() == SchemaDraftSourceType.DOCUMENT).forEach(this::refreshObservedStatus);
        return sources.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SourceResponse refreshDocument(
        String knowledgeBaseId, String draftId, String sourceId, long draftRevision
    ) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        SchemaDraftSourceNode source = requireSource(draftId, sourceId);
        if (source.getType() != SchemaDraftSourceType.DOCUMENT) {
            throw new IllegalArgumentException("Only document sources can be refreshed");
        }
        DocumentUploadNode document = requireDocument(knowledgeBaseId, source.getDocumentId());
        source.setRevision(source.getRevision() + 1);
        source.setSha256(document.getSha256());
        source.setSizeBytes(document.getSizeBytes());
        source.setStatus(SchemaDraftSourceStatus.ACTIVE);
        source.setUpdatedAt(Instant.now());
        sourceRepository.save(source);
        snapshot(source);
        lifecycleService.advance(draft);
        draft.setCurrentAggregateId(null);
        draftRepository.save(draft);
        return toResponse(source);
    }

    @Transactional
    public void remove(String knowledgeBaseId, String draftId, String sourceId, long draftRevision) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        SchemaDraftSourceNode source = requireSource(draftId, sourceId);
        if (source.isAnalyzed()) {
            source.setStatus(SchemaDraftSourceStatus.INACTIVE);
            source.setRevision(source.getRevision() + 1);
            source.setUpdatedAt(Instant.now());
            sourceRepository.save(source);
            snapshot(source);
        } else {
            deleteOwnedContent(source);
            sourceRepository.delete(source);
        }
        lifecycleService.advance(draft);
        draft.setCurrentAggregateId(null);
        draftRepository.save(draft);
    }

    @Transactional
    public SourceResponse restore(String knowledgeBaseId, String draftId, String sourceId, long draftRevision) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        SchemaDraftSourceNode source = requireSource(draftId, sourceId);
        if (source.getStatus() != SchemaDraftSourceStatus.INACTIVE) {
            throw new ConflictException("Only inactive draft sources can be restored");
        }
        if (source.getType() == SchemaDraftSourceType.DOCUMENT) {
            DocumentUploadNode document = requireDocument(knowledgeBaseId, source.getDocumentId());
            if (!source.getSha256().equals(document.getSha256())) {
                throw new ConflictException("Document source changed and must be refreshed before restoration");
            }
        }
        source.setStatus(SchemaDraftSourceStatus.ACTIVE);
        source.setRevision(source.getRevision() + 1);
        source.setUpdatedAt(Instant.now());
        sourceRepository.save(source);
        snapshot(source);
        lifecycleService.advance(draft);
        draftRepository.save(draft);
        return toResponse(source);
    }

    private SourceResponse addOwned(
        String knowledgeBaseId, String draftId, long draftRevision, SchemaDraftSourceType type,
        String name, String contentType, byte[] bytes
    ) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        String fingerprint = sha256(bytes);
        java.util.Optional<SchemaDraftSourceNode> duplicate = existing(draftId, type, fingerprint);
        if (duplicate.isPresent()) {
            return toResponse(duplicate.get());
        }
        String sourceId = UUID.randomUUID().toString();
        SchemaDraftStorageMutationNode mutation = mutationService.begin(
            SchemaDraftStorageMutationType.STORE, draftId, sourceId, null);
        try {
            URI uri = storageService.storeDraftSource(knowledgeBaseId, draftId, sourceId, name, bytes);
            mutationService.recordContent(mutation.getId(), uri.toString());
            SourceResponse response = create(draft, sourceId, type, name, contentType, bytes.length, fingerprint, null, uri.toString());
            mutationService.complete(mutation.getId());
            return response;
        } catch (Exception exception) {
            mutationService.failure(mutation.getId(), exception);
            throw new IllegalStateException("Draft source could not be stored", exception);
        }
    }

    @Transactional
    protected SourceResponse create(
        SchemaDraftNode draft, String requestedSourceId, SchemaDraftSourceType type, String name, String contentType, long sizeBytes,
        String fingerprint, String documentId, String contentUri
    ) {
        Instant now = Instant.now();
        SchemaDraftSourceNode source = new SchemaDraftSourceNode();
        source.setId(requestedSourceId == null ? UUID.randomUUID().toString() : requestedSourceId);
        source.setDraftId(draft.getId());
        source.setKnowledgeBaseId(draft.getKnowledgeBaseId());
        source.setType(type);
        source.setStatus(SchemaDraftSourceStatus.ACTIVE);
        source.setRevision(0);
        source.setDocumentId(documentId);
        source.setName(name);
        source.setContentType(contentType);
        source.setSizeBytes(sizeBytes);
        source.setSha256(fingerprint);
        source.setContentUri(contentUri);
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        SchemaDraftSourceNode saved = sourceRepository.save(source);
        graphService.attach(draft.getId(), "SchemaDraftSource", saved.getId());
        snapshot(saved);
        lifecycleService.advance(draft);
        draft.setCurrentAggregateId(null);
        draftRepository.save(draft);
        log.info("Schema draft source added: knowledgeBaseId={}, draftId={}, sourceId={}, type={}, revision={}",
            draft.getKnowledgeBaseId(), draft.getId(), saved.getId(), type, draft.getRevision());
        return toResponse(saved);
    }

    private java.util.Optional<SchemaDraftSourceNode> existing(
        String draftId, SchemaDraftSourceType type, String fingerprint
    ) {
        return sourceRepository.findFirstByDraftIdAndTypeAndSha256AndStatus(
            draftId, type, fingerprint, SchemaDraftSourceStatus.ACTIVE);
    }

    private void snapshot(SchemaDraftSourceNode source) {
        SchemaDraftSourceRevisionNode revision = new SchemaDraftSourceRevisionNode();
        revision.setId(source.getId() + ":" + source.getRevision());
        revision.setDraftId(source.getDraftId());
        revision.setSourceId(source.getId());
        revision.setRevision(source.getRevision());
        revision.setStatus(source.getStatus());
        revision.setSha256(source.getSha256());
        revision.setDocumentId(source.getDocumentId());
        revision.setContentUri(source.getContentUri());
        revision.setCreatedAt(Instant.now());
        revisionRepository.save(revision);
        graphService.attach(source.getDraftId(), "SchemaDraftSourceRevision", revision.getId());
    }

    private void refreshObservedStatus(SchemaDraftSourceNode source) {
        DocumentUploadNode document = documentRepository.findById(source.getDocumentId()).orElse(null);
        SchemaDraftSourceStatus observed = document == null ? SchemaDraftSourceStatus.UNAVAILABLE
            : source.getSha256().equals(document.getSha256()) ? source.getStatus() : SchemaDraftSourceStatus.STALE;
        if (source.getStatus() != SchemaDraftSourceStatus.INACTIVE && observed != source.getStatus()) {
            source.setStatus(observed);
            source.setUpdatedAt(Instant.now());
            sourceRepository.save(source);
        }
    }

    private SchemaDraftSourceNode requireSource(String draftId, String sourceId) {
        return sourceRepository.findByIdAndDraftId(sourceId, draftId)
            .orElseThrow(() -> new NotFoundException("Draft source not found: " + sourceId));
    }

    private DocumentUploadNode requireDocument(String knowledgeBaseId, String documentId) {
        DocumentUploadNode document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found in knowledge base: " + documentId));
        if (!knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            throw new NotFoundException("Document not found in knowledge base: " + documentId);
        }
        return document;
    }

    private void deleteOwnedContent(SchemaDraftSourceNode source) {
        if (source.getContentUri() == null || source.getContentUri().isBlank()) {
            return;
        }
        SchemaDraftStorageMutationNode mutation = mutationService.begin(
            SchemaDraftStorageMutationType.DELETE, source.getDraftId(), source.getId(), source.getContentUri());
        try {
            storageService.delete(URI.create(source.getContentUri()));
            mutationService.complete(mutation.getId());
        } catch (Exception exception) {
            mutationService.failure(mutation.getId(), exception);
            throw new ConflictException("Draft source storage cleanup failed: " + source.getId());
        }
    }

    private void validateBytes(byte[] bytes, int characters) {
        RuntimeSettingsService.DiscoverySettings limits = runtimeSettingsService.discovery();
        if (bytes.length > limits.maxSourceBytes()) {
            throw new IllegalArgumentException("Draft source exceeds byte limit " + limits.maxSourceBytes());
        }
        if (characters > limits.maxSourceCharacters()) {
            throw new IllegalArgumentException("Draft text exceeds character limit " + limits.maxSourceCharacters());
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safeName(String value) {
        return value == null || value.isBlank() ? "source.bin" : value;
    }

    public SourceResponse toResponse(SchemaDraftSourceNode source) {
        return new SourceResponse(source.getId(), source.getType(), source.getStatus(), source.getRevision(),
            source.getDocumentId(), source.getName(), source.getContentType(), source.getSizeBytes(), source.getSha256(),
            source.isAnalyzed(), source.getCreatedAt(), source.getUpdatedAt());
    }
}
