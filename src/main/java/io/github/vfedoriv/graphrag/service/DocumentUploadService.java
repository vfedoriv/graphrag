package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class DocumentUploadService {

    private final BinaryStorageService binaryStorageService;
    private final DocumentUploadRepository documentUploadRepository;
    private final Neo4jClient neo4jClient;

    public DocumentUploadService(
        BinaryStorageService binaryStorageService,
        DocumentUploadRepository documentUploadRepository,
        Neo4jClient neo4jClient
    ) {
        this.binaryStorageService = binaryStorageService;
        this.documentUploadRepository = documentUploadRepository;
        this.neo4jClient = neo4jClient;
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

        try {
            URI contentUri = binaryStorageService.store(knowledgeBaseId, node.getId(), file.getOriginalFilename(), bytes);
            node.setContentUri(contentUri.toString());
            DocumentUploadNode saved = documentUploadRepository.save(node);
            log.info("Document uploaded: knowledgeBaseId={}, documentId={}, bytes={}", knowledgeBaseId, saved.getId(), bytes.length);
            return saved;
        } catch (IOException ex) {
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
        validateFile(file);
        DocumentUploadNode document = findInKnowledgeBase(knowledgeBaseId, documentId);
        byte[] bytes = readBytes(file);
        String sha256 = sha256(bytes);
        Optional<DocumentUploadNode> duplicate = documentUploadRepository.findByKnowledgeBaseIdAndSha256(knowledgeBaseId, sha256);
        if (duplicate.isPresent() && !documentId.equals(duplicate.get().getId())) {
            throw new ConflictException("Replacement duplicates another document in knowledge base: " + duplicate.get().getId());
        }

        String previousContentUri = document.getContentUri();
        URI replacementContentUri;
        try {
            replacementContentUri = binaryStorageService.store(knowledgeBaseId, documentId, file.getOriginalFilename(), bytes);
        } catch (IOException ex) {
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

        CleanupResult cleanupResult = cleanupDerivedArtifacts(documentId);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setSizeBytes(bytes.length);
        document.setSha256(sha256);
        document.setContentUri(replacementContentUri.toString());
        document.setStatus(DocumentStatus.UPLOADED);
        document.setProcessedAt(null);
        document.setErrorMessage(null);
        DocumentUploadNode saved = documentUploadRepository.save(document);
        deletePreviousContent(previousContentUri, replacementContentUri.toString(), documentId);
        log.info(
            "Document replaced: knowledgeBaseId={}, documentId={}, deletedChunks={}, deletedRuns={}, deletedRelationships={}, deletedObsoleteExtractedNodes={}",
            knowledgeBaseId,
            documentId,
            cleanupResult.deletedChunks(),
            cleanupResult.deletedRuns(),
            cleanupResult.deletedRelationships(),
            cleanupResult.deletedObsoleteExtractedNodes()
        );
        return saved;
    }

    @Transactional
    public void delete(String knowledgeBaseId, String documentId) {
        log.info("Deleting document: knowledgeBaseId={}, documentId={}", knowledgeBaseId, documentId);
        DocumentUploadNode document = findInKnowledgeBase(knowledgeBaseId, documentId);
        deletePrimaryContent(document);
        CleanupResult cleanupResult = cleanupDerivedArtifacts(documentId);
        documentUploadRepository.delete(document);
        log.info(
            "Document deleted: knowledgeBaseId={}, documentId={}, deletedChunks={}, deletedRuns={}, deletedRelationships={}, deletedObsoleteExtractedNodes={}",
            knowledgeBaseId,
            documentId,
            cleanupResult.deletedChunks(),
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

    private void deletePreviousContent(String previousContentUri, String replacementContentUri, String documentId) {
        if (previousContentUri == null || previousContentUri.isBlank() || previousContentUri.equals(replacementContentUri)) {
            return;
        }
        try {
            binaryStorageService.delete(URI.create(previousContentUri));
        } catch (IOException ex) {
            log.warn(
                "Previous document content cleanup failed after replacement: documentId={}, contentUri={}, message={}",
                documentId,
                previousContentUri,
                ex.getMessage(),
                ex
            );
        }
    }

    private CleanupResult cleanupDerivedArtifacts(String documentId) {
        Map<String, Object> cleanupRow = neo4jClient.query("""
            MATCH (document:DocumentUpload {id: $documentId})
            OPTIONAL MATCH (document)-[:HAS_CHUNK]->(chunk:DocumentChunk)
            WITH document, collect(DISTINCT chunk) AS chunks
            FOREACH (chunk IN chunks | DETACH DELETE chunk)
            WITH document, size(chunks) AS deletedChunks
            OPTIONAL MATCH (document)-[:HAS_EXTRACTION_RUN]->(run:ExtractionRun)
            WITH document, deletedChunks, [run IN collect(DISTINCT run) WHERE run IS NOT NULL] AS runsToDelete
            WITH document, deletedChunks, runsToDelete, [run IN runsToDelete | run.id] AS runIds
            OPTIONAL MATCH ()-[graphRel]-()
            WHERE graphRel.sourceDocumentId = $documentId
                AND (size(runIds) = 0 OR graphRel.extractionRunId IN runIds)
            WITH document, deletedChunks, runsToDelete, collect(DISTINCT graphRel) AS graphRelationships
            FOREACH (graphRel IN graphRelationships | DELETE graphRel)
            WITH document, deletedChunks, runsToDelete, size(graphRelationships) AS deletedGraphRelationshipCount
            UNWIND CASE WHEN size(runsToDelete) = 0 THEN [null] ELSE runsToDelete END AS runToDelete
            OPTIONAL MATCH (runToDelete)-[runRel]-()
            WITH document, deletedChunks, runsToDelete, deletedGraphRelationshipCount, count(DISTINCT runRel) AS deletedRunRelationshipCount
            FOREACH (run IN runsToDelete | DETACH DELETE run)
            WITH
                document,
                deletedChunks,
                size(runsToDelete) AS deletedRuns,
                deletedGraphRelationshipCount + deletedRunRelationshipCount AS deletedRelationshipCount
            OPTIONAL MATCH (obsoleteNode)
            WHERE obsoleteNode.sourceDocumentId = $documentId
                AND NOT obsoleteNode:ExtractionRun
                AND NOT obsoleteNode:DocumentUpload
                AND NOT obsoleteNode:DocumentChunk
                AND NOT obsoleteNode:KnowledgeBase
                AND NOT obsoleteNode:SchemaDefinition
                AND NOT (obsoleteNode)<-[:CREATED_NODE]-(:ExtractionRun)
            WITH deletedChunks, deletedRuns, deletedRelationshipCount, collect(DISTINCT obsoleteNode) AS obsoleteNodes
            FOREACH (obsoleteNode IN obsoleteNodes | DETACH DELETE obsoleteNode)
            RETURN
                deletedChunks AS deletedChunks,
                deletedRuns AS deletedRuns,
                deletedRelationshipCount AS deletedRelationships,
                size(obsoleteNodes) AS deletedObsoleteExtractedNodes
            """)
            .bind(documentId).to("documentId")
            .fetch()
            .one()
            .orElse(null);
        if (cleanupRow == null) {
            log.warn("Document cleanup returned no row: documentId={}", documentId);
            return CleanupResult.zero();
        }
        return new CleanupResult(
            toLong(cleanupRow.get("deletedChunks")),
            toLong(cleanupRow.get("deletedRuns")),
            toLong(cleanupRow.get("deletedRelationships")),
            toLong(cleanupRow.get("deletedObsoleteExtractedNodes"))
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private record CleanupResult(
        long deletedChunks,
        long deletedRuns,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        private static CleanupResult zero() {
            return new CleanupResult(0L, 0L, 0L, 0L);
        }
    }
}
