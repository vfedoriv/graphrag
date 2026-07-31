package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.repository.ParentContextRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

@Repository
public class Neo4jParentContextRepository implements ParentContextRepository {

    private final Neo4jClient neo4jClient;

    public Neo4jParentContextRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public List<ParentContextRow> load(
        String knowledgeBaseId,
        List<ParentContextCandidate> candidates,
        int adjacentChunks
    ) {
        List<Map<String, Object>> bindings = candidates.stream()
            .map(candidate -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("rank", candidate.rank());
                value.put("chunkId", candidate.chunkId());
                value.put("documentId", candidate.documentId());
                value.put("processingRunId", candidate.processingRunId());
                value.put("strategyRevision", candidate.strategyRevision());
                return value;
            })
            .toList();
        return neo4jClient.query(parentExpansionCypher())
            .bind(bindings).to("candidates")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(adjacentChunks).to("adjacentChunks")
            .fetch()
            .all()
            .stream()
            .map(this::toRow)
            .toList();
    }

    private ParentContextRow toRow(Map<String, Object> row) {
        return new ParentContextRow(
            intValue(row.get("rank")),
            stringValue(row.get("childId")),
            stringValue(row.get("documentId")),
            stringValue(row.get("strategyRevision")),
            stringValue(row.get("outcome")),
            stringValue(row.get("parentId")),
            stringValue(row.get("parentText")),
            nullableInt(row.get("parentTokenEstimate")),
            nullableInt(row.get("parentSourceStart")),
            nullableInt(row.get("parentSourceEnd")),
            nullableInt(row.get("parentPageStart")),
            nullableInt(row.get("parentPageEnd")),
            adjacentChunks(row.get("adjacency"))
        );
    }

    private List<AdjacentChunk> adjacentChunks(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<AdjacentChunk> chunks = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            chunks.add(new AdjacentChunk(
                stringValue(map.get("id")),
                stringValue(map.get("text")),
                nullableInt(map.get("tokenEstimate")),
                nullableInt(map.get("sourceStart")),
                nullableInt(map.get("sourceEnd")),
                nullableInt(map.get("pageStart")),
                nullableInt(map.get("pageEnd"))
            ));
        }
        return chunks;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Integer nullableInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String parentExpansionCypher() {
        return """
            UNWIND $candidates AS candidate
            OPTIONAL MATCH (child:DocumentChunk {id: candidate.chunkId})
            OPTIONAL MATCH (referencedParent:DocumentChunk {id: child.parentChunkId})
            OPTIONAL MATCH (referencedParent)-[membership:HAS_CHILD]->(child)
            WITH candidate, child, referencedParent, membership,
                 CASE
                   WHEN child IS NULL THEN 'CHILD_NOT_FOUND'
                   WHEN child.knowledgeBaseId <> $knowledgeBaseId OR child.documentId <> candidate.documentId
                     THEN 'CHILD_SCOPE'
                   WHEN coalesce(child.processingRunId, '') <> coalesce(candidate.processingRunId, '') THEN 'CHILD_RUN'
                   WHEN coalesce(child.effectiveChunkerRevision, '') <> coalesce(candidate.strategyRevision, '')
                     THEN 'CHILD_REVISION'
                   WHEN child.parentChunkId IS NULL THEN 'NO_PARENT'
                   WHEN referencedParent IS NULL THEN 'PARENT_NOT_FOUND'
                   WHEN referencedParent.kind <> 'PARENT' THEN 'PARENT_KIND'
                   WHEN referencedParent.knowledgeBaseId <> child.knowledgeBaseId
                     OR referencedParent.documentId <> child.documentId THEN 'PARENT_SCOPE'
                   WHEN coalesce(referencedParent.processingRunId, '') <> coalesce(child.processingRunId, '')
                     THEN 'PARENT_RUN'
                   WHEN coalesce(referencedParent.effectiveChunkerRevision, '')
                     <> coalesce(child.effectiveChunkerRevision, '') THEN 'PARENT_REVISION'
                   WHEN membership IS NULL THEN 'MEMBERSHIP'
                   ELSE 'VALID_PARENT'
                 END AS outcome
            CALL {
              WITH child, outcome
              OPTIONAL MATCH (adjacent:DocumentChunk {documentId: child.documentId})
              WHERE outcome IN ['NO_PARENT', 'PARENT_NOT_FOUND']
                AND (adjacent.kind IS NULL OR adjacent.kind = 'CHILD')
                AND adjacent.knowledgeBaseId = child.knowledgeBaseId
                AND coalesce(adjacent.processingRunId, '') = coalesce(child.processingRunId, '')
                AND coalesce(adjacent.effectiveChunkerRevision, '') = coalesce(child.effectiveChunkerRevision, '')
                AND abs(adjacent.chunkIndex - child.chunkIndex) <= $adjacentChunks
                AND (
                  (child.parentChunkId IS NULL
                    AND adjacent.sectionIndex = child.sectionIndex
                    AND coalesce(adjacent.structuralPath, '') = coalesce(child.structuralPath, ''))
                  OR
                  (child.parentChunkId IS NOT NULL AND adjacent.parentChunkId = child.parentChunkId)
                )
              WITH adjacent ORDER BY adjacent.chunkIndex
              RETURN [item IN collect(adjacent) WHERE item IS NOT NULL | {
                id: item.id,
                text: item.text,
                tokenEstimate: item.tokenEstimate,
                sourceStart: item.sourceStart,
                sourceEnd: item.sourceEnd,
                pageStart: item.pageStart,
                pageEnd: item.pageEnd
              }] AS adjacency
            }
            RETURN
              candidate.rank AS rank,
              candidate.chunkId AS childId,
              candidate.documentId AS documentId,
              candidate.strategyRevision AS strategyRevision,
              outcome AS outcome,
              referencedParent.id AS parentId,
              referencedParent.text AS parentText,
              referencedParent.tokenEstimate AS parentTokenEstimate,
              referencedParent.sourceStart AS parentSourceStart,
              referencedParent.sourceEnd AS parentSourceEnd,
              referencedParent.pageStart AS parentPageStart,
              referencedParent.pageEnd AS parentPageEnd,
              adjacency AS adjacency
            ORDER BY rank
            """;
    }
}
