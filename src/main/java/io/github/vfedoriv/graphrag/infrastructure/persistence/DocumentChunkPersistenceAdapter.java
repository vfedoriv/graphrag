package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.document.chunking.ChunkHashes;
import io.github.vfedoriv.graphrag.document.chunking.ChunkKind;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunkPersistenceAdapter {

    private final DocumentChunkRepository repository;
    private final EmbeddingSpaceIndexService embeddingSpaceIndexService;
    private final Neo4jClient neo4jClient;

    public DocumentChunkPersistenceAdapter(
        DocumentChunkRepository repository,
        EmbeddingSpaceIndexService embeddingSpaceIndexService,
        Neo4jClient neo4jClient
    ) {
        this.repository = repository;
        this.embeddingSpaceIndexService = embeddingSpaceIndexService;
        this.neo4jClient = neo4jClient;
    }

    @GraphTransactional
    public void replace(String documentId, String knowledgeBaseId, EmbeddingSpace embeddingSpace, List<DocumentChunkNode> chunks) {
        requireScope(documentId, knowledgeBaseId, chunks);
        requireHierarchy(chunks);
        repository.deleteByDocumentId(documentId);
        for (DocumentChunkNode chunk : chunks) {
            repository.save(chunk);
            if (isRetrievalChild(chunk)) {
                embeddingSpaceIndexService.assignChunk(chunk.getId(), knowledgeBaseId, embeddingSpace);
            }
        }
        createHierarchyRelationships(chunks);
        requirePersistedIntegrity(documentId);
    }

    public void prepareRetrievalIndex(String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        embeddingSpaceIndexService.ensureIndex(knowledgeBaseId, embeddingSpace);
    }

    public List<DocumentChunkNode> findByDocumentId(String documentId) {
        return repository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    private void requireScope(String documentId, String knowledgeBaseId, List<DocumentChunkNode> chunks) {
        requireNonBlank(documentId, "documentId");
        requireNonBlank(knowledgeBaseId, "knowledgeBaseId");
        Set<String> ids = new HashSet<>();
        for (DocumentChunkNode chunk : chunks) {
            if (!knowledgeBaseId.equals(chunk.getKnowledgeBaseId()) || !documentId.equals(chunk.getDocumentId())) {
                throw new IllegalArgumentException("Chunk scope must match the persistence boundary");
            }
            if (chunk.getId() == null || !ids.add(chunk.getId())) {
                throw new IllegalArgumentException("Chunk identifiers must be present and unique");
            }
        }
    }

    private void requireHierarchy(List<DocumentChunkNode> chunks) {
        Map<String, DocumentChunkNode> parents = new HashMap<>();
        for (DocumentChunkNode chunk : chunks) {
            if (ChunkKind.PARENT.name().equals(chunk.getKind())) {
                if (chunk.getParentChunkId() != null || chunk.getChildIndex() != null) {
                    throw new IllegalArgumentException("Parent chunks cannot reference another hierarchy node");
                }
                if (chunk.getEmbedding() != null || chunk.getEmbeddingSpaceId() != null) {
                    throw new IllegalArgumentException("Parent chunks cannot contain retrieval embeddings");
                }
                if (!ChunkHashes.sha256(chunk.getText()).equals(chunk.getSourceHash())) {
                    throw new IllegalArgumentException("Parent source hash must match its materialized text");
                }
                parents.put(chunk.getId(), chunk);
            }
        }
        if (parents.isEmpty()) {
            return;
        }

        Map<String, List<DocumentChunkNode>> childrenByParent = new HashMap<>();
        for (DocumentChunkNode child : chunks) {
            if (!ChunkKind.CHILD.name().equals(child.getKind())) {
                continue;
            }
            DocumentChunkNode parent = parents.get(child.getParentChunkId());
            if (parent == null) {
                throw new IllegalArgumentException("Every hierarchical child must reference an existing parent");
            }
            requireSameHierarchyScope(parent, child);
            requireContained(parent, child);
            childrenByParent.computeIfAbsent(parent.getId(), ignored -> new ArrayList<>()).add(child);
        }
        for (DocumentChunkNode parent : parents.values()) {
            List<DocumentChunkNode> children = childrenByParent.getOrDefault(parent.getId(), List.of()).stream()
                .sorted(Comparator.comparing(DocumentChunkNode::getChildIndex))
                .toList();
            if (children.size() != parent.getChildCount()) {
                throw new IllegalArgumentException("Parent childCount does not match its contained children");
            }
            for (int index = 0; index < children.size(); index++) {
                if (children.get(index).getChildIndex() == null || children.get(index).getChildIndex() != index) {
                    throw new IllegalArgumentException("Children must have consecutive parent-relative ordering");
                }
            }
        }
    }

    private void requireSameHierarchyScope(DocumentChunkNode parent, DocumentChunkNode child) {
        if (!parent.getKnowledgeBaseId().equals(child.getKnowledgeBaseId())
            || !parent.getDocumentId().equals(child.getDocumentId())
            || !parent.getProcessingRunId().equals(child.getProcessingRunId())
            || !parent.getEffectiveChunkerRevision().equals(child.getEffectiveChunkerRevision())) {
            throw new IllegalArgumentException("Parent and child hierarchy scope or revision does not match");
        }
    }

    private void requireContained(DocumentChunkNode parent, DocumentChunkNode child) {
        if (parent.getPageStart() != null && (child.getPageStart() == null
            || child.getPageStart() < parent.getPageStart()
            || child.getPageEnd() > parent.getPageEnd()
            || !child.getPageStart().equals(child.getPageEnd()))) {
            throw new IllegalArgumentException("Child page range must be page-bounded within its parent");
        }
        if (parent.getPageStart() == null || parent.getPageStart().equals(child.getPageStart())) {
            if (parent.getSourceStart() != null && child.getSourceStart() < parent.getSourceStart()) {
                throw new IllegalArgumentException("Child source range starts before its parent");
            }
        }
        if (parent.getPageEnd() == null || parent.getPageEnd().equals(child.getPageEnd())) {
            if (parent.getSourceEnd() != null && child.getSourceEnd() > parent.getSourceEnd()) {
                throw new IllegalArgumentException("Child source range ends after its parent");
            }
        }
    }

    private void createHierarchyRelationships(List<DocumentChunkNode> chunks) {
        List<Map<String, Object>> memberships = chunks.stream()
            .filter(chunk -> ChunkKind.CHILD.name().equals(chunk.getKind()))
            .filter(chunk -> chunk.getParentChunkId() != null)
            .map(chunk -> Map.<String, Object>of(
                "parentId", chunk.getParentChunkId(),
                "childId", chunk.getId(),
                "ordinal", chunk.getChildIndex()
            ))
            .toList();
        if (memberships.isEmpty()) {
            return;
        }
        neo4jClient.query("""
            UNWIND $memberships AS membership
            MATCH (parent:DocumentChunk {id: membership.parentId, kind: 'PARENT'})
            MATCH (child:DocumentChunk {id: membership.childId, kind: 'CHILD'})
            MERGE (parent)-[relationship:HAS_CHILD]->(child)
            SET relationship.ordinal = membership.ordinal
            """)
            .bind(memberships).to("memberships")
            .run();
    }

    private void requirePersistedIntegrity(String documentId) {
        Long invalid = neo4jClient.query("""
            MATCH (child:DocumentChunk {documentId: $documentId, kind: 'CHILD'})
            WHERE child.parentChunkId IS NOT NULL
              AND NOT EXISTS {
                MATCH (parent:DocumentChunk {id: child.parentChunkId, kind: 'PARENT'})-[:HAS_CHILD]->(child)
                WHERE parent.knowledgeBaseId = child.knowledgeBaseId
                  AND parent.documentId = child.documentId
                  AND parent.processingRunId = child.processingRunId
                  AND parent.effectiveChunkerRevision = child.effectiveChunkerRevision
              }
            RETURN count(child) AS invalid
            """)
            .bind(documentId).to("documentId")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
        if (invalid > 0L) {
            throw new IllegalStateException("Persisted hierarchy contains orphan or mixed-revision children");
        }
    }

    private boolean isRetrievalChild(DocumentChunkNode chunk) {
        return !ChunkKind.PARENT.name().equals(chunk.getKind()) && chunk.getEmbedding() != null;
    }

    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
