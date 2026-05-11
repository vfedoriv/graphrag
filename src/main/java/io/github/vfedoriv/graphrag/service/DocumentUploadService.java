package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class DocumentUploadService {

    private final BinaryStorageService binaryStorageService;
    private final DocumentUploadRepository documentUploadRepository;

    public DocumentUploadService(BinaryStorageService binaryStorageService, DocumentUploadRepository documentUploadRepository) {
        this.binaryStorageService = binaryStorageService;
        this.documentUploadRepository = documentUploadRepository;
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

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes: filename={}, message={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new IllegalArgumentException("Cannot read uploaded file bytes", e);
        }
    }
}
