package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationType;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class DocumentUploadService {

    private final BinaryStorageService binaryStorageService;
    private final DocumentUploadRepository documentUploadRepository;
    private final GraphArtifactCleanupService graphArtifactCleanupService;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final DocumentStorageMutationService storageMutationService;

    public DocumentUploadService(
        BinaryStorageService binaryStorageService,
        DocumentUploadRepository documentUploadRepository,
        GraphArtifactCleanupService graphArtifactCleanupService,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        DocumentStorageMutationService storageMutationService
    ) {
        this.binaryStorageService = binaryStorageService;
        this.documentUploadRepository = documentUploadRepository;
        this.graphArtifactCleanupService = graphArtifactCleanupService;
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.storageMutationService = storageMutationService;
    }

    @Transactional
    public DocumentUploadNode upload(String knowledgeBaseId, MultipartFile file) {
        log.info(
            "Uploading document: knowledgeBaseId={}, filename={}, contentType={}, sizeBytes={}",
            knowledgeBaseId,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize()
        );
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        validateFile(file);
        byte[] bytes = readBytes(file);
        String sha256 = sha256(bytes);

        Optional<DocumentUploadNode> existing = documentUploadRepository.findByKnowledgeBaseIdAndSha256(knowledgeBaseId, sha256);
        if (existing.isPresent()) {
            log.info(
                "Document upload deduplicated: knowledgeBaseId={}, existingDocumentId={}, sha256={}",
                knowledgeBaseId,
                existing.get().getId(),
                sha256
            );
            return existing.get();
        }

        DocumentUploadNode node = new DocumentUploadNode();
        node.setId(UUID.randomUUID().toString());
        node.setKnowledgeBaseId(knowledgeBaseId);
        node.setOriginalFilename(file.getOriginalFilename());
        node.setContentType(file.getContentType());
        node.setSizeBytes(bytes.length);
        node.setSha256(sha256);
        node.setStatus(DocumentStatus.UPLOADED);
        node.setUploadedAt(Instant.now());

        DocumentStorageMutationNode mutation = storageMutationService.begin(
            DocumentStorageMutationType.STORE, knowledgeBaseId, node.getId(), null, null
        );
        try {
            URI contentUri = binaryStorageService.store(knowledgeBaseId, node.getId(), file.getOriginalFilename(), bytes);
            storageMutationService.recordStoredContent(mutation.getId(), contentUri.toString());
            node.setContentUri(contentUri.toString());
            DocumentUploadNode saved = documentUploadRepository.save(node);
            completeAfterCommit(mutation.getId());
            log.info("Document uploaded: knowledgeBaseId={}, documentId={}, bytes={}", knowledgeBaseId, saved.getId(), bytes.length);
            return saved;
        } catch (IOException ex) {
            storageMutationService.recordFailure(mutation.getId(), ex);
            log.error(
                "Binary storage failed during document upload: knowledgeBaseId={}, documentId={}, filename={}, message={}",
                knowledgeBaseId,
                node.getId(),
                file.getOriginalFilename(),
                ex.getMessage(),
                ex
            );
            node.setStatus(DocumentStatus.FAILED);
            node.setErrorMessage("Binary storage failed: " + ex.getMessage());
            return documentUploadRepository.save(node);
        }
    }

    public String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 digest algorithm is unavailable", e);
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public byte[] readContent(String contentUri) throws IOException {
        log.info("Reading document content from storage: contentUri={}", contentUri);
        try (InputStream stream = binaryStorageService.read(URI.create(contentUri))) {
            byte[] bytes = stream.readAllBytes();
            log.info("Document content read from storage: contentUri={}, bytes={}", contentUri, bytes.length);
            return bytes;
        }
    }

    public List<DocumentUploadNode> listByKnowledgeBase(String knowledgeBaseId) {
        log.info("Listing uploaded documents: knowledgeBaseId={}", knowledgeBaseId);
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        List<DocumentUploadNode> documents = documentUploadRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(knowledgeBaseId);
        log.info("Uploaded documents listed: knowledgeBaseId={}, count={}", knowledgeBaseId, documents.size());
        return documents;
    }

    public String localPath(DocumentUploadNode document) {
        if (document.getContentUri() == null || document.getContentUri().isBlank()) {
            return null;
        }
        Path path = binaryStorageService.resolvePath(URI.create(document.getContentUri()));
        return path.toString();
    }

    @Transactional
    public DocumentUploadNode replace(String knowledgeBaseId, String documentId, MultipartFile file) {
        log.info(
            "Replacing document: knowledgeBaseId={}, documentId={}, filename={}, contentType={}, sizeBytes={}",
            knowledgeBaseId,
            documentId,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize()
        );
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        validateFile(file);
        DocumentUploadNode document = findInKnowledgeBase(knowledgeBaseId, documentId);
        byte[] bytes = readBytes(file);
        String sha256 = sha256(bytes);
        Optional<DocumentUploadNode> duplicate = documentUploadRepository.findByKnowledgeBaseIdAndSha256(knowledgeBaseId, sha256);
        if (duplicate.isPresent() && !documentId.equals(duplicate.get().getId())) {
            throw new ConflictException("Replacement duplicates another document in knowledge base: " + duplicate.get().getId());
        }

        String previousContentUri = document.getContentUri();
        DocumentStorageMutationNode storeMutation = storageMutationService.begin(
            DocumentStorageMutationType.STORE, knowledgeBaseId, documentId, null, previousContentUri
        );
        URI replacementContentUri;
        try {
            replacementContentUri = binaryStorageService.store(knowledgeBaseId, documentId, file.getOriginalFilename(), bytes);
            storageMutationService.recordStoredContent(storeMutation.getId(), replacementContentUri.toString());
        } catch (IOException ex) {
            storageMutationService.recordFailure(storeMutation.getId(), ex);
            log.error(
                "Binary storage failed during document replacement: knowledgeBaseId={}, documentId={}, filename={}, message={}",
                knowledgeBaseId,
                documentId,
                file.getOriginalFilename(),
                ex.getMessage(),
                ex
            );
            throw new IllegalStateException("Binary storage failed: " + ex.getMessage(), ex);
        }

        GraphArtifactCleanupService.DocumentArtifactCleanupResult cleanupResult =
            graphArtifactCleanupService.cleanupDocumentArtifacts(documentId);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setSizeBytes(bytes.length);
        document.setSha256(sha256);
        document.setContentUri(replacementContentUri.toString());
        document.setStatus(DocumentStatus.UPLOADED);
        document.setProcessedAt(null);
        document.setErrorMessage(null);
        DocumentUploadNode saved = documentUploadRepository.save(document);
        completeAfterCommit(storeMutation.getId());
        schedulePreviousContentDeletion(knowledgeBaseId, documentId, previousContentUri, replacementContentUri.toString());
        log.info(
            "Document replaced: knowledgeBaseId={}, documentId={}, deletedChunks={}, deletedProcessingRuns={}, deletedRuns={}, deletedRelationships={}, deletedObsoleteExtractedNodes={}",
            knowledgeBaseId,
            documentId,
            cleanupResult.deletedChunks(),
            cleanupResult.deletedProcessingRuns(),
            cleanupResult.deletedRuns(),
            cleanupResult.deletedRelationships(),
            cleanupResult.deletedObsoleteExtractedNodes()
        );
        return saved;
    }

    @Transactional
    public void delete(String knowledgeBaseId, String documentId) {
        log.info("Deleting document: knowledgeBaseId={}, documentId={}", knowledgeBaseId, documentId);
        knowledgeBaseLifecycleService.requireManaged(knowledgeBaseId);
        DocumentUploadNode document = findInKnowledgeBase(knowledgeBaseId, documentId);
        DocumentStorageMutationNode mutation = storageMutationService.begin(
            DocumentStorageMutationType.DELETE, knowledgeBaseId, documentId, document.getContentUri(), null
        );
        try {
            deletePrimaryContent(document);
        } catch (IllegalStateException ex) {
            storageMutationService.recordFailure(mutation.getId(), ex);
            throw ex;
        }
        GraphArtifactCleanupService.DocumentArtifactCleanupResult cleanupResult =
            graphArtifactCleanupService.cleanupDocumentArtifacts(documentId);
        documentUploadRepository.delete(document);
        completeAfterCommit(mutation.getId());
        log.info(
            "Document deleted: knowledgeBaseId={}, documentId={}, deletedChunks={}, deletedProcessingRuns={}, deletedRuns={}, deletedRelationships={}, deletedObsoleteExtractedNodes={}",
            knowledgeBaseId,
            documentId,
            cleanupResult.deletedChunks(),
            cleanupResult.deletedProcessingRuns(),
            cleanupResult.deletedRuns(),
            cleanupResult.deletedRelationships(),
            cleanupResult.deletedObsoleteExtractedNodes()
        );
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes: filename={}, message={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new IllegalArgumentException("Cannot read uploaded file bytes", e);
        }
    }

    private DocumentUploadNode findInKnowledgeBase(String knowledgeBaseId, String documentId) {
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        if (!knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            throw new NotFoundException("Document not found in knowledge base: " + documentId);
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file must not be empty");
        }
    }

    private void deletePrimaryContent(DocumentUploadNode document) {
        if (document.getContentUri() == null || document.getContentUri().isBlank()) {
            return;
        }
        try {
            binaryStorageService.delete(URI.create(document.getContentUri()));
        } catch (IOException ex) {
            log.error(
                "Binary storage failed during document deletion: documentId={}, contentUri={}, message={}",
                document.getId(),
                document.getContentUri(),
                ex.getMessage(),
                ex
            );
            throw new IllegalStateException("Binary storage delete failed: " + ex.getMessage(), ex);
        }
    }

    private void schedulePreviousContentDeletion(
        String knowledgeBaseId,
        String documentId,
        String previousContentUri,
        String replacementContentUri
    ) {
        if (previousContentUri == null || previousContentUri.isBlank() || previousContentUri.equals(replacementContentUri)) {
            return;
        }
        DocumentStorageMutationNode mutation = storageMutationService.begin(
            DocumentStorageMutationType.DELETE_REPLACED_CONTENT, knowledgeBaseId, documentId, previousContentUri, null
        );
        try {
            binaryStorageService.delete(URI.create(previousContentUri));
            completeAfterCommit(mutation.getId());
        } catch (IOException ex) {
            storageMutationService.recordFailure(mutation.getId(), ex);
            log.warn(
                "Previous document content cleanup failed after replacement: documentId={}, contentUri={}, message={}",
                documentId,
                previousContentUri,
                ex.getMessage(),
                ex
            );
        }
    }

    private void completeAfterCommit(String mutationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageMutationService.complete(mutationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageMutationService.complete(mutationId);
            }
        });
    }

}
