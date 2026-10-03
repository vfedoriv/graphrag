package io.github.vfedoriv.graphrag.documents.application.management;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentMetadata;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentMetadataAccess;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentMetadataFacade implements DocumentMetadataAccess {
    private final DocumentUploadRepository documents;

    public DocumentMetadataFacade(DocumentUploadRepository documents) {
        this.documents = documents;
    }

    @Override
    public List<DocumentMetadata> findOwnedBatch(String knowledgeBaseId, List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> boundedIds = new LinkedHashSet<>();
        for (String documentId : documentIds) {
            if (documentId != null && !documentId.isBlank()) {
                boundedIds.add(documentId);
                if (boundedIds.size() == MAX_BATCH_DOCUMENT_IDS) {
                    break;
                }
            }
        }
        if (boundedIds.isEmpty()) {
            return List.of();
        }

        return documents.findAllByIdInAndKnowledgeBaseId(new ArrayList<>(boundedIds), knowledgeBaseId)
            .stream()
            .map(this::metadataOf)
            .toList();
    }

    @Override
    public List<String> selectOwned(String knowledgeBaseId, String filename, String contentType, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        int boundedLimit = Math.min(limit, MAX_SELECTION_RESULTS);
        return documents.findByMetadata(knowledgeBaseId, filename, contentType, boundedLimit)
            .stream()
            .map(DocumentUploadNode::getId)
            .toList();
    }

    private DocumentMetadata metadataOf(DocumentUploadNode document) {
        return new DocumentMetadata(document.getId(), document.getOriginalFilename(), document.getContentType());
    }
}
