package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.document.chunking.ChunkKind;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.neo4j.core.Neo4jClient;

public final class DocumentChunkTopologyClassifier {

    private static final String COUNTS_RETURN = """
        RETURN documentId AS documentId,
               size(chunks) AS chunkCount,
               size([chunk IN chunks WHERE chunk.kind = 'PARENT']) AS parentCount,
               size([chunk IN chunks WHERE chunk.kind = 'CHILD' AND chunk.parentChunkId IS NOT NULL]) AS parentedChildCount,
               size([chunk IN chunks WHERE chunk.kind = 'CHILD' AND chunk.parentChunkId IS NULL]) AS unparentedChildCount,
               size([child IN chunks WHERE child.kind = 'CHILD'
                   AND child.parentChunkId IS NOT NULL
                   AND NOT any(parent IN chunks WHERE parent.kind = 'PARENT' AND parent.id = child.parentChunkId)]) AS orphanChildCount,
               size([child IN chunks WHERE child.kind = 'CHILD'
                   AND child.parentChunkId IS NOT NULL
                   AND any(parent IN chunks WHERE parent.kind = 'PARENT' AND parent.id = child.parentChunkId)
                   AND NOT any(parent IN chunks WHERE parent.kind = 'PARENT'
                       AND parent.id = child.parentChunkId
                       AND parent.knowledgeBaseId = child.knowledgeBaseId
                       AND parent.documentId = child.documentId
                       AND parent.processingRunId = child.processingRunId
                       AND parent.effectiveChunkerRevision = child.effectiveChunkerRevision)]) AS crossScopeChildCount,
               size([chunk IN chunks WHERE chunk.kind IS NULL OR NOT chunk.kind IN ['PARENT', 'CHILD']]) AS unsupportedKindCount,
               size([parent IN chunks WHERE parent.kind = 'PARENT'
                   AND (parent.parentChunkId IS NOT NULL OR parent.childIndex IS NOT NULL)]) AS malformedParentCount,
               size([child IN chunks WHERE child.kind = 'CHILD'
                   AND child.parentChunkId IS NOT NULL
                   AND any(parent IN chunks WHERE parent.kind = 'PARENT'
                       AND parent.id = child.parentChunkId
                       AND parent.knowledgeBaseId = child.knowledgeBaseId
                       AND parent.documentId = child.documentId
                       AND parent.processingRunId = child.processingRunId
                       AND parent.effectiveChunkerRevision = child.effectiveChunkerRevision)
                   AND NOT any(parent IN chunks WHERE parent.kind = 'PARENT'
                       AND parent.id = child.parentChunkId
                       AND parent.knowledgeBaseId = child.knowledgeBaseId
                       AND parent.documentId = child.documentId
                       AND parent.processingRunId = child.processingRunId
                       AND parent.effectiveChunkerRevision = child.effectiveChunkerRevision
                       AND EXISTS { MATCH (parent)-[:HAS_CHILD]->(child) })]) AS missingHierarchyRelationshipCount
        """;

    private static final String DOCUMENT_COUNTS_QUERY = """
        OPTIONAL MATCH (chunk:DocumentChunk {documentId: $documentId})
        WITH [candidate IN collect(chunk) WHERE candidate IS NOT NULL] AS chunks, $documentId AS documentId
        """ + COUNTS_RETURN;

    private static final String AUDIT_QUERY = """
        MATCH (chunk:DocumentChunk)
        WITH chunk.documentId AS documentId, collect(chunk) AS chunks
        """ + COUNTS_RETURN + """
        ORDER BY documentId ASC
        """;

    private final Neo4jClient neo4jClient;

    public DocumentChunkTopologyClassifier(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public DocumentChunkTopology classify(List<DocumentChunkNode> chunks) {
        return assessInput(chunks).topology();
    }

    public void requireValidInput(List<DocumentChunkNode> chunks) {
        InputAssessment assessment = assessInput(chunks);
        if (assessment.crossScopeChildCount() > 0) {
            throw new IllegalArgumentException("Parent and child hierarchy scope or revision does not match");
        }
        if (assessment.orphanChildCount() > 0) {
            throw new IllegalArgumentException("Every hierarchical child must reference an existing parent");
        }
        if (assessment.topology() == DocumentChunkTopology.INVALID) {
            throw new IllegalArgumentException("Document chunk topology is invalid");
        }
    }

    public DocumentChunkTopology classifyDocument(String documentId) {
        Optional<Map<String, Object>> row = neo4jClient.query(DOCUMENT_COUNTS_QUERY)
            .bind(documentId).to("documentId")
            .fetch()
            .one();
        return toAuditRow(row.orElseGet(Map::of)).topology();
    }

    public List<DocumentChunkTopologyAuditRow> audit() {
        List<DocumentChunkTopologyAuditRow> auditRows = new ArrayList<>();
        for (Map<String, Object> row : neo4jClient.query(AUDIT_QUERY).fetch().all()) {
            auditRows.add(toAuditRow(row));
        }
        return List.copyOf(auditRows);
    }

    private InputAssessment assessInput(List<DocumentChunkNode> chunks) {
        Map<String, DocumentChunkNode> parents = new HashMap<>();
        long parentCount = 0L;
        long parentedChildCount = 0L;
        long unparentedChildCount = 0L;
        long orphanChildCount = 0L;
        long crossScopeChildCount = 0L;
        long unsupportedKindCount = 0L;
        long malformedParentCount = 0L;

        for (DocumentChunkNode chunk : chunks) {
            if (ChunkKind.PARENT.name().equals(chunk.getKind())) {
                parentCount++;
                if (chunk.getParentChunkId() != null || chunk.getChildIndex() != null) {
                    malformedParentCount++;
                }
                parents.put(chunk.getId(), chunk);
            } else if (ChunkKind.CHILD.name().equals(chunk.getKind())) {
                if (chunk.getParentChunkId() == null) {
                    unparentedChildCount++;
                } else {
                    parentedChildCount++;
                }
            } else {
                unsupportedKindCount++;
            }
        }

        for (DocumentChunkNode chunk : chunks) {
            if (!ChunkKind.CHILD.name().equals(chunk.getKind()) || chunk.getParentChunkId() == null) {
                continue;
            }
            DocumentChunkNode parent = parents.get(chunk.getParentChunkId());
            if (parent == null) {
                orphanChildCount++;
                continue;
            }
            if (!sameScope(parent, chunk)) {
                crossScopeChildCount++;
            }
        }

        return new InputAssessment(
            chunks.size(),
            parentCount,
            parentedChildCount,
            unparentedChildCount,
            orphanChildCount,
            crossScopeChildCount,
            unsupportedKindCount,
            malformedParentCount
        );
    }

    private DocumentChunkTopologyAuditRow toAuditRow(Map<String, Object> row) {
        String documentId = stringValue(row.get("documentId"));
        long chunkCount = longValue(row, "chunkCount");
        long parentCount = longValue(row, "parentCount");
        long parentedChildCount = longValue(row, "parentedChildCount");
        long unparentedChildCount = longValue(row, "unparentedChildCount");
        long orphanChildCount = longValue(row, "orphanChildCount");
        long crossScopeChildCount = longValue(row, "crossScopeChildCount");
        long unsupportedKindCount = longValue(row, "unsupportedKindCount");
        long malformedParentCount = longValue(row, "malformedParentCount");
        long missingHierarchyRelationshipCount = longValue(row, "missingHierarchyRelationshipCount");
        DocumentChunkTopologyAuditRow counts = new DocumentChunkTopologyAuditRow(
            documentId,
            chunkCount,
            parentCount,
            parentedChildCount,
            unparentedChildCount,
            orphanChildCount,
            crossScopeChildCount,
            unsupportedKindCount,
            malformedParentCount,
            missingHierarchyRelationshipCount,
            DocumentChunkTopology.EMPTY
        );
        return new DocumentChunkTopologyAuditRow(
            documentId,
            chunkCount,
            parentCount,
            parentedChildCount,
            unparentedChildCount,
            orphanChildCount,
            crossScopeChildCount,
            unsupportedKindCount,
            malformedParentCount,
            missingHierarchyRelationshipCount,
            topology(counts)
        );
    }

    private DocumentChunkTopology topology(DocumentChunkTopologyAuditRow row) {
        if (row.chunkCount() == 0L) {
            return DocumentChunkTopology.EMPTY;
        }
        if (row.orphanChildCount() > 0L
            || row.crossScopeChildCount() > 0L
            || row.unsupportedKindCount() > 0L
            || row.malformedParentCount() > 0L
            || row.missingHierarchyRelationshipCount() > 0L) {
            return DocumentChunkTopology.INVALID;
        }
        if (row.parentCount() == 0L
            && row.parentedChildCount() == 0L
            && row.unparentedChildCount() == row.chunkCount()) {
            return DocumentChunkTopology.FLAT;
        }
        if (row.parentCount() > 0L && row.unparentedChildCount() == 0L) {
            return DocumentChunkTopology.HIERARCHICAL;
        }
        return DocumentChunkTopology.INVALID;
    }

    private boolean sameScope(DocumentChunkNode parent, DocumentChunkNode child) {
        return Objects.equals(parent.getKnowledgeBaseId(), child.getKnowledgeBaseId())
            && Objects.equals(parent.getDocumentId(), child.getDocumentId())
            && Objects.equals(parent.getProcessingRunId(), child.getProcessingRunId())
            && Objects.equals(parent.getEffectiveChunkerRevision(), child.getEffectiveChunkerRevision());
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record InputAssessment(
        long chunkCount,
        long parentCount,
        long parentedChildCount,
        long unparentedChildCount,
        long orphanChildCount,
        long crossScopeChildCount,
        long unsupportedKindCount,
        long malformedParentCount
    ) {
        private DocumentChunkTopology topology() {
            if (chunkCount == 0L) {
                return DocumentChunkTopology.EMPTY;
            }
            if (orphanChildCount > 0L
                || crossScopeChildCount > 0L
                || unsupportedKindCount > 0L
                || malformedParentCount > 0L) {
                return DocumentChunkTopology.INVALID;
            }
            if (parentCount == 0L && parentedChildCount == 0L && unparentedChildCount == chunkCount) {
                return DocumentChunkTopology.FLAT;
            }
            if (parentCount > 0L && unparentedChildCount == 0L) {
                return DocumentChunkTopology.HIERARCHICAL;
            }
            return DocumentChunkTopology.INVALID;
        }
    }
}
