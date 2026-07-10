package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.error.EmbeddingSpaceConflictException;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingSpacePolicy {

    private final DocumentChunkRepository documentChunkRepository;

    public EmbeddingSpacePolicy(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    public EmbeddingSpace spaceFor(AiProfileNode profile) {
        return EmbeddingSpaceIdentity.fromProfile(profile);
    }

    public void requireCompatible(String knowledgeBaseId, AiProfileNode profile) {
        requireCompatible(knowledgeBaseId, spaceFor(profile));
    }

    public boolean hasEmbeddedChunks(String knowledgeBaseId) {
        return !embeddedChunks(knowledgeBaseId).isEmpty();
    }

    public void requireCompatible(String knowledgeBaseId, EmbeddingSpace targetSpace) {
        List<DocumentChunkNode> chunks = embeddedChunks(knowledgeBaseId);
        List<String> incompatibleChunkIds = new ArrayList<>();
        for (DocumentChunkNode chunk : chunks) {
            if (!targetSpace.id().equals(chunk.getEmbeddingSpaceId())) {
                incompatibleChunkIds.add(chunk.getId());
            }
        }
        if (!incompatibleChunkIds.isEmpty()) {
            throw new EmbeddingSpaceConflictException(
                "AI profile embedding space is incompatible with stored embeddings in knowledge base "
                    + knowledgeBaseId + ". Re-embed legacy or incompatible chunks before changing the profile.",
                List.of(knowledgeBaseId)
            );
        }
    }

    public List<String> incompatibleKnowledgeBaseIds(List<String> knowledgeBaseIds, EmbeddingSpace targetSpace) {
        List<String> incompatibleKnowledgeBaseIds = new ArrayList<>();
        for (String knowledgeBaseId : knowledgeBaseIds) {
            try {
                requireCompatible(knowledgeBaseId, targetSpace);
            } catch (EmbeddingSpaceConflictException ex) {
                incompatibleKnowledgeBaseIds.add(knowledgeBaseId);
            }
        }
        return incompatibleKnowledgeBaseIds;
    }

    private List<DocumentChunkNode> embeddedChunks(String knowledgeBaseId) {
        List<DocumentChunkNode> chunks = documentChunkRepository.findEmbeddedChunksByKnowledgeBaseId(knowledgeBaseId);
        return chunks == null ? List.of() : chunks;
    }
}
