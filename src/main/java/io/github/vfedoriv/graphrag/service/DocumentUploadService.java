package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private final BinaryStorageService binaryStorageService;
    private final DocumentUploadRepository documentUploadRepository;

    public DocumentUploadService(BinaryStorageService binaryStorageService, DocumentUploadRepository documentUploadRepository) {
        this.binaryStorageService = binaryStorageService;
        this.documentUploadRepository = documentUploadRepository;
    }

    @Transactional
    public DocumentUploadNode upload(String knowledgeBaseId, MultipartFile file) {
        byte[] bytes = readBytes(file);
        String sha256 = sha256(bytes);

        var existing = documentUploadRepository.findByKnowledgeBaseIdAndSha256(knowledgeBaseId, sha256);
        if (existing.isPresent()) {
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
            return documentUploadRepository.save(node);
        } catch (IOException ex) {
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
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public byte[] readContent(String contentUri) throws IOException {
        try (var stream = binaryStorageService.read(URI.create(contentUri))) {
            return stream.readAllBytes();
        }
    }

    public List<DocumentUploadNode> listByKnowledgeBase(String knowledgeBaseId) {
        return documentUploadRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(knowledgeBaseId);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read uploaded file bytes", e);
        }
    }
}
