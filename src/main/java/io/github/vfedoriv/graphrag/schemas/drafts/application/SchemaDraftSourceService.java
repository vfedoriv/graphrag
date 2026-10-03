package io.github.vfedoriv.graphrag.schemas.drafts.application;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.DraftSourceFingerprint;

import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftDocumentInputs;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceRevisionNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.SourceResponse;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRevisionRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftBinaryStorage;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class SchemaDraftSourceService {
    private final SchemaDraftLifecycleService lifecycleService;
    private final SchemaDraftRepository draftRepository;
    private final SchemaDraftSourceRepository sourceRepository;
    private final SchemaDraftSourceRevisionRepository revisionRepository;
    private final DraftDocumentInputs documentRepository;
    private final DraftBinaryStorage storageService;
    private final SchemaDraftStorageMutationService mutationService;
    private final RuntimeSettingsAccess runtimeSettingsService;
    private final TransactionTemplate transactionTemplate;

    public SchemaDraftSourceService(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftRepository draftRepository,
        SchemaDraftSourceRepository sourceRepository,
        SchemaDraftSourceRevisionRepository revisionRepository,
        DraftDocumentInputs documentRepository,
        DraftBinaryStorage storageService,
        SchemaDraftStorageMutationService mutationService,
        RuntimeSettingsAccess runtimeSettingsService,
        TransactionTemplate transactionTemplate
    ) {
        this.lifecycleService = lifecycleService;
        this.draftRepository = draftRepository;
        this.sourceRepository = sourceRepository;
        this.revisionRepository = revisionRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.mutationService = mutationService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.transactionTemplate = transactionTemplate;
    }

    @RelationalTransactional
    public SourceResponse addDocument(String knowledgeBaseId, String draftId, long draftRevision, String documentId) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        DraftDocumentInputs.Metadata document = requireDocument(knowledgeBaseId, documentId);
        return existing(draftId, SchemaDraftSourceType.DOCUMENT, document.sha256())
            .map(this::toResponse)
            .orElseGet(() -> create(draft, null, SchemaDraftSourceType.DOCUMENT, document.filename(),
                document.contentType(), document.sizeBytes(), document.sha256(), document.documentId(), null));
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

    @RelationalTransactional
    public List<SourceResponse> list(String knowledgeBaseId, String draftId) {
        lifecycleService.requireOwned(knowledgeBaseId, draftId);
        List<SchemaDraftSourceNode> sources = sourceRepository.findByDraftIdOrderByCreatedAtAsc(draftId);
        sources.stream().filter(source -> source.getType() == SchemaDraftSourceType.DOCUMENT).forEach(source -> refreshObservedStatus(knowledgeBaseId, source));
        return sources.stream().map(this::toResponse).toList();
    }

    @RelationalTransactional
    public SourceResponse refreshDocument(
        String knowledgeBaseId, String draftId, String sourceId, long draftRevision
    ) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        SchemaDraftSourceNode source = requireSource(draftId, sourceId);
        if (source.getType() != SchemaDraftSourceType.DOCUMENT) {
            throw new IllegalArgumentException("Only document sources can be refreshed");
        }
        DraftDocumentInputs.Metadata document = requireDocument(knowledgeBaseId, source.getDocumentId());
        source.setRevision(source.getRevision() + 1);
        source.setSha256(document.sha256());
        source.setSizeBytes(document.sizeBytes());
        source.setStatus(SchemaDraftSourceStatus.ACTIVE);
        source.setUpdatedAt(Instant.now());
        sourceRepository.save(source);
        snapshot(source);
        lifecycleService.advance(draft);
        draft.setCurrentAggregateId(null);
        draftRepository.save(draft);
        return toResponse(source);
    }

    public void remove(String knowledgeBaseId, String draftId, String sourceId, long draftRevision) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        SchemaDraftSourceNode source = requireSource(draftId, sourceId);
        if (source.isAnalyzed()) {
            transactionTemplate.executeWithoutResult(status ->
                removeMetadata(knowledgeBaseId, draftId, sourceId, draftRevision, false));
        } else {
            SchemaDraftStorageMutationNode mutation = beginOwnedContentDeletion(source);
            transactionTemplate.executeWithoutResult(status ->
                removeMetadata(knowledgeBaseId, draftId, sourceId, draftRevision, true));
            finishOwnedContentDeletion(mutation);
        }
    }

    @RelationalTransactional
    public SourceResponse restore(String knowledgeBaseId, String draftId, String sourceId, long draftRevision) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        SchemaDraftSourceNode source = requireSource(draftId, sourceId);
        if (source.getStatus() != SchemaDraftSourceStatus.INACTIVE) {
            throw new ConflictException("Only inactive draft sources can be restored");
        }
        if (source.getType() == SchemaDraftSourceType.DOCUMENT) {
            DraftDocumentInputs.Metadata document = requireDocument(knowledgeBaseId, source.getDocumentId());
            if (!source.getSha256().equals(document.sha256())) {
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
        String fingerprint = DraftSourceFingerprint.sha256(bytes);
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
            SourceResponse response = transactionTemplate.execute(status ->
                create(draft, sourceId, type, name, contentType, bytes.length, fingerprint, null, uri.toString()));
            if (response == null) {
                throw new IllegalStateException("Draft source relational checkpoint returned no result");
            }
            mutationService.complete(mutation.getId());
            return response;
        } catch (Exception exception) {
            mutationService.failure(mutation.getId(), exception);
            throw new IllegalStateException("Draft source could not be stored", exception);
        }
    }

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
    }

    private void refreshObservedStatus(String knowledgeBaseId, SchemaDraftSourceNode source) {
        DraftDocumentInputs.Metadata document = documentRepository.inspectOwned(knowledgeBaseId, source.getDocumentId()).orElse(null);
        SchemaDraftSourceStatus observed = document == null ? SchemaDraftSourceStatus.UNAVAILABLE
            : source.getSha256().equals(document.sha256()) ? source.getStatus() : SchemaDraftSourceStatus.STALE;
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

    private DraftDocumentInputs.Metadata requireDocument(String knowledgeBaseId, String documentId) {
        return documentRepository.inspectOwned(knowledgeBaseId, documentId)
            .orElseThrow(() -> new NotFoundException("Document not found in knowledge base: " + documentId));
    }

    private SchemaDraftStorageMutationNode beginOwnedContentDeletion(SchemaDraftSourceNode source) {
        return mutationService.begin(
            SchemaDraftStorageMutationType.DELETE, source.getDraftId(), source.getId(), source.getContentUri());
    }

    private void finishOwnedContentDeletion(SchemaDraftStorageMutationNode mutation) {
        try {
            if (mutation.getContentUri() != null && !mutation.getContentUri().isBlank()) {
                storageService.delete(URI.create(mutation.getContentUri()));
            }
            mutationService.complete(mutation.getId());
        } catch (Exception exception) {
            mutationService.failure(mutation.getId(), exception);
            throw new ConflictException("Draft source storage cleanup failed: " + mutation.getId());
        }
    }

    private void removeMetadata(
        String knowledgeBaseId, String draftId, String sourceId, long draftRevision, boolean delete
    ) {
        SchemaDraftNode currentDraft = lifecycleService.requireMutable(knowledgeBaseId, draftId, draftRevision);
        SchemaDraftSourceNode currentSource = requireSource(draftId, sourceId);
        if (delete) {
            sourceRepository.delete(currentSource);
        } else {
            currentSource.setStatus(SchemaDraftSourceStatus.INACTIVE);
            currentSource.setRevision(currentSource.getRevision() + 1);
            currentSource.setUpdatedAt(Instant.now());
            sourceRepository.save(currentSource);
            snapshot(currentSource);
        }
        lifecycleService.advance(currentDraft);
        currentDraft.setCurrentAggregateId(null);
        draftRepository.save(currentDraft);
    }

    private void validateBytes(byte[] bytes, int characters) {
        RuntimeSettingsAccess.DiscoverySettings limits = runtimeSettingsService.discovery();
        if (bytes.length > limits.maxSourceBytes()) {
            throw new IllegalArgumentException("Draft source exceeds byte limit " + limits.maxSourceBytes());
        }
        if (characters > limits.maxSourceCharacters()) {
            throw new IllegalArgumentException("Draft text exceeds character limit " + limits.maxSourceCharacters());
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
