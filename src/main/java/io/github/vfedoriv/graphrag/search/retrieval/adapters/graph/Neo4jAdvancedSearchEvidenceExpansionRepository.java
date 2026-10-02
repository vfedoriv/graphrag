package io.github.vfedoriv.graphrag.search.retrieval.adapters.graph;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.FactKind;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ParentCitation;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.SchemaRepresentation;
import io.github.vfedoriv.graphrag.search.retrieval.ports.AdvancedSearchEvidenceExpansionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

@Repository
public class Neo4jAdvancedSearchEvidenceExpansionRepository
    implements AdvancedSearchEvidenceExpansionRepository {

    private final Neo4jClient neo4jClient;

    public Neo4jAdvancedSearchEvidenceExpansionRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public List<ExpansionRow> expand(String knowledgeBaseId, List<String> seedChunkIds, int maxFacts) {
        if (seedChunkIds.isEmpty() || maxFacts < 1) {
            return List.of();
        }
        return neo4jClient.query(expansionCypher())
            .bind(seedChunkIds).to("seedChunkIds")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(maxFacts).to("maxFacts")
            .fetch()
            .all()
            .stream()
            .map(this::toRow)
            .filter(row -> row != null)
            .toList();
    }

    String expansionCypher() {
        return """
            UNWIND range(0, size($seedChunkIds) - 1) AS seedRank
            WITH seedRank, $seedChunkIds[seedRank] AS seedChunkId
            MATCH (seed:DocumentChunk {id: seedChunkId, knowledgeBaseId: $knowledgeBaseId})
            OPTIONAL MATCH (parent:DocumentChunk {id: seed.parentChunkId, knowledgeBaseId: $knowledgeBaseId})
              -[:HAS_CHILD]->(seed)
            WITH seedRank, seedChunkId, coalesce(parent, seed) AS evidenceChunk
            MATCH (evidenceChunk)-[:HAS_GRAPH_EVIDENCE]->(evidence:GraphExtractionEvidence)
            MATCH (evidence)-[assertion:ASSERTS_NODE|ASSERTS_FROM|ASSERTS_TO]->(fact)
            WITH seedRank, seedChunkId, evidence, assertion, fact
            ORDER BY seedRank, evidence.id, type(assertion), elementId(fact)
            LIMIT $maxFacts
            RETURN seedChunkId,
                   evidence.canonicalFactId AS canonicalFactId,
                   evidence.factKind AS factKind,
                   coalesce(evidence.schemaType, head(labels(fact))) AS schemaType,
                   evidence.fromLabel AS fromLabel,
                   evidence.toLabel AS toLabel,
                   properties(fact) AS properties,
                   evidence.id AS evidenceId,
                   evidence.sourceChunkId AS sourceChunkId,
                   evidence.sourceDocumentId AS sourceDocumentId,
                   evidence.sourceStart AS sourceStart,
                   evidence.sourceEnd AS sourceEnd,
                   evidence.pageStart AS pageStart,
                   evidence.pageEnd AS pageEnd,
                   evidence.processingRunId AS processingRunId,
                   evidence.effectiveChunkerRevision AS effectiveChunkerRevision,
                   evidence.structuralPath AS structuralPath
            """;
    }

    private ExpansionRow toRow(Map<String, Object> row) {
        String seedChunkId = stringValue(row.get("seedChunkId"));
        String canonicalFactId = stringValue(row.get("canonicalFactId"));
        String sourceChunkId = stringValue(row.get("sourceChunkId"));
        String sourceDocumentId = stringValue(row.get("sourceDocumentId"));
        String evidenceId = stringValue(row.get("evidenceId"));
        FactKind factKind = factKind(row.get("factKind"));
        if (!hasText(seedChunkId) || !hasText(canonicalFactId) || !hasText(sourceChunkId)
            || !hasText(sourceDocumentId) || !hasText(evidenceId) || factKind == null) {
            return null;
        }
        ParentCitation citation = new ParentCitation(
            sourceChunkId,
            sourceDocumentId,
            integerValue(row.get("sourceStart")),
            integerValue(row.get("sourceEnd")),
            integerValue(row.get("pageStart")),
            integerValue(row.get("pageEnd")),
            stringValue(row.get("processingRunId")),
            stringValue(row.get("effectiveChunkerRevision")),
            stringValue(row.get("structuralPath"))
        );
        GraphFact fact = new GraphFact(
            canonicalFactId,
            new SchemaRepresentation(
                factKind,
                stringValue(row.get("schemaType")),
                stringValue(row.get("fromLabel")),
                stringValue(row.get("toLabel"))
            ),
            objectMap(row.get("properties")),
            List.of(evidenceId),
            List.of(citation)
        );
        return new ExpansionRow(seedChunkId, fact);
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private FactKind factKind(Object value) {
        try {
            return FactKind.valueOf(stringValue(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
