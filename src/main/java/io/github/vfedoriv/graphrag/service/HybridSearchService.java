package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.dto.HybridSearchGraphContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphEvidence;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphEntity;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphRelationship;
import io.github.vfedoriv.graphrag.dto.HybridSearchHit;
import io.github.vfedoriv.graphrag.dto.HybridSearchRequest;
import io.github.vfedoriv.graphrag.dto.HybridSearchResponse;
import io.github.vfedoriv.graphrag.dto.HybridSearchRetrievalEvidence;
import io.github.vfedoriv.graphrag.dto.HybridSearchSource;
import io.github.vfedoriv.graphrag.dto.HybridSearchSourceRange;
import io.github.vfedoriv.graphrag.dto.HybridSearchCitationKind;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HybridSearchService {

    private final RuntimeSettingsService runtimeSettingsService;
    private final ProfileScopedAiClientResolver aiClientResolver;
    private final Neo4jClient neo4jClient;
    private final DocumentUploadRepository documentUploadRepository;
    private final ParentContextExpansionService parentContextExpansionService;
    private final QueryEvidenceAssemblyService queryEvidenceAssemblyService;
    private final DenseTextRetriever denseTextRetriever;

    public HybridSearchService(
        RuntimeSettingsService runtimeSettingsService,
        ProfileScopedAiClientResolver aiClientResolver,
        Neo4jClient neo4jClient,
        DocumentUploadRepository documentUploadRepository,
        ParentContextExpansionService parentContextExpansionService,
        QueryEvidenceAssemblyService queryEvidenceAssemblyService,
        DenseTextRetriever denseTextRetriever
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.aiClientResolver = aiClientResolver;
        this.neo4jClient = neo4jClient;
        this.documentUploadRepository = documentUploadRepository;
        this.parentContextExpansionService = parentContextExpansionService;
        this.queryEvidenceAssemblyService = queryEvidenceAssemblyService;
        this.denseTextRetriever = denseTextRetriever;
    }

    public HybridSearchResponse search(String knowledgeBaseId, HybridSearchRequest request) {
        long startNanos = System.nanoTime();
        int topK = boundedTopK(request.topK());
        int graphDepth = boundedGraphDepth(request.graphDepth());
        boolean includeChunkText = request.includeChunkText() == null
            ? runtimeSettingsService.query().hybridSearchIncludeChunkText()
            : request.includeChunkText();
        int candidateCount = candidateCount(topK);
        log.info(
            "Hybrid search starting: knowledgeBaseId={}, queryLength={}, topK={}, candidateCount={}, graphDepth={}, includeChunkText={}",
            knowledgeBaseId,
            LogMetadata.length(request.query()),
            topK,
            candidateCount,
            graphDepth,
            includeChunkText
        );

        EmbeddingClient embeddingClient = resolveEmbeddingClient();
        if (embeddingClient == null) {
            throw new IllegalStateException("Embedding model is not configured for hybrid search");
        }
        Result denseResult = denseTextRetriever.retrieve(new Request(
            knowledgeBaseId,
            List.of(new Subquery("hybrid-query", request.query())),
            new MetadataConstraints(null, null),
            candidateCount,
            false,
            Instant.MAX
        ));
        if (denseResult.diagnostics().status() != Status.COMPLETED) {
            throw new IllegalStateException(
                "Dense retrieval failed for hybrid search: " + denseResult.diagnostics().failureCategory()
            );
        }
        if (denseResult.candidates().isEmpty()) {
            return emptyResponse(request, topK, graphDepth, includeChunkText, startNanos, knowledgeBaseId);
        }
        List<Map<String, Object>> candidateRows = denseResult.candidates().stream()
            .map(this::candidateRow)
            .toList();

        List<Map<String, Object>> rows = new ArrayList<>(neo4jClient.query(hybridSearchCypher(graphDepth))
            .bind(candidateRows).to("candidates")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(topK).to("topK")
            .bind(includeChunkText).to("includeChunkText")
            .fetch()
            .all());
        List<HybridSearchHit> baseHits = enrichHits(knowledgeBaseId, rows, includeChunkText);
        ParentContextExpansionService.ExpansionResult expansion = parentContextExpansionService.expand(
            knowledgeBaseId,
            baseHits,
            runtimeSettingsService.query(),
            includeChunkText
        );
        QueryEvidenceAssemblyService.QueryEvidenceAssembly evidenceAssembly =
            queryEvidenceAssemblyService.assemble(expansion.hits(), expansion.contexts());
        List<HybridSearchHit> hits = evidenceAssembly.hits();
        HybridSearchResponse response = new HybridSearchResponse(
            request.query(),
            topK,
            graphDepth,
            includeChunkText,
            hits,
            hits.size(),
            LogMetadata.elapsedMillis(startNanos),
            evidenceAssembly.contexts(),
            evidenceAssembly.graphEvidence(),
            expansion.diagnostics()
        );
        log.info(
            "Hybrid search completed: knowledgeBaseId={}, hitCount={}, executionTimeMs={}",
            knowledgeBaseId,
            response.hitCount(),
            response.executionTimeMs()
        );
        return response;
    }

    private Map<String, Object> candidateRow(Candidate candidate) {
        return Map.of(
            "chunkId", candidate.source().chunkId(),
            "score", candidate.rawScore(),
            "rank", candidate.rank()
        );
    }

    private HybridSearchResponse emptyResponse(
        HybridSearchRequest request,
        int topK,
        int graphDepth,
        boolean includeChunkText,
        long startNanos,
        String knowledgeBaseId
    ) {
        HybridSearchResponse response = new HybridSearchResponse(
            request.query(),
            topK,
            graphDepth,
            includeChunkText,
            List.of(),
            0,
            LogMetadata.elapsedMillis(startNanos)
        );
        log.info("Hybrid search completed: knowledgeBaseId={}, hitCount=0, executionTimeMs={}",
            knowledgeBaseId, response.executionTimeMs());
        return response;
    }

    private int boundedTopK(Integer requestedTopK) {
        RuntimeSettingsService.QuerySettings settings = runtimeSettingsService.query();
        int topK = requestedTopK == null ? settings.hybridSearchDefaultTopK() : requestedTopK;
        if (topK > settings.hybridSearchMaxTopK()) {
            throw new IllegalArgumentException("topK must be less than or equal to " + settings.hybridSearchMaxTopK());
        }
        return topK;
    }

    private int boundedGraphDepth(Integer requestedGraphDepth) {
        RuntimeSettingsService.QuerySettings settings = runtimeSettingsService.query();
        int graphDepth = requestedGraphDepth == null ? settings.hybridSearchDefaultGraphDepth() : requestedGraphDepth;
        if (graphDepth > settings.hybridSearchMaxGraphDepth()) {
            throw new IllegalArgumentException("graphDepth must be less than or equal to " + settings.hybridSearchMaxGraphDepth());
        }
        return graphDepth;
    }

    private int candidateCount(int topK) {
        RuntimeSettingsService.QuerySettings settings = runtimeSettingsService.query();
        long multiplied = (long) topK * settings.hybridSearchCandidateMultiplier();
        return (int) Math.min(multiplied, settings.hybridSearchMaxCandidates());
    }

    List<HybridSearchHit> enrichHits(
        String knowledgeBaseId,
        List<Map<String, Object>> rows,
        boolean includeChunkText
    ) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> documentIds = rows.stream()
            .map(row -> stringValue(row.get("documentId")))
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        List<DocumentUploadNode> documents = documentUploadRepository == null
            ? List.of()
            : documentUploadRepository.findAllByIdInAndKnowledgeBaseId(documentIds, knowledgeBaseId);
        Map<String, DocumentUploadNode> byId = new LinkedHashMap<>();
        for (DocumentUploadNode document : documents) {
            byId.put(document.getId(), document);
        }
        return rows.stream()
            .filter(row -> byId.containsKey(stringValue(row.get("documentId"))))
            .map(row -> toHit(row, byId.get(stringValue(row.get("documentId"))), includeChunkText))
            .toList();
    }

    private HybridSearchHit toHit(
        Map<String, Object> row,
        DocumentUploadNode document,
        boolean includeChunkText
    ) {
        String chunkId = stringValue(row.get("chunkId"));
        String documentId = stringValue(row.get("documentId"));
        int chunkIndex = intValue(row.get("chunkIndex"));
        double score = doubleValue(row.get("score"));
        String text = includeChunkText ? stringValue(row.get("text")) : null;
        HybridSearchSource source = new HybridSearchSource(
            documentId,
            document.getOriginalFilename(),
            document.getContentType(),
            document.getSizeBytes(),
            stringValue(row.get("chunkMetadata"))
        );
        return new HybridSearchHit(
            chunkId,
            documentId,
            chunkIndex,
            score,
            text,
            source,
            new HybridSearchGraphContext(
                toEntities(row.get("entities")),
                toRelationships(row.get("relationships")),
                toGraphEvidence(row.get("graphEvidence"))
            ),
            new HybridSearchRetrievalEvidence(
                chunkId,
                text,
                new HybridSearchSourceRange(
                    nullableIntValue(row.get("sourceStart")),
                    nullableIntValue(row.get("sourceEnd")),
                    nullableIntValue(row.get("pageStart")),
                    nullableIntValue(row.get("pageEnd"))
                ),
                stringValue(row.get("processingRunId")),
                stringValue(row.get("effectiveChunkerRevision")),
                stringValue(row.get("structuralPath")),
                HybridSearchCitationKind.TEXT_CHILD
            ),
            null
        );
    }

    private List<HybridSearchGraphEntity> toEntities(Object value) {
        List<HybridSearchGraphEntity> entities = new ArrayList<>();
        for (Map<String, Object> item : mapList(value)) {
            entities.add(new HybridSearchGraphEntity(
                stringValue(item.get("elementId")),
                stringList(item.get("labels")),
                objectMap(item.get("properties"))
            ));
        }
        return entities;
    }

    private List<HybridSearchGraphRelationship> toRelationships(Object value) {
        List<HybridSearchGraphRelationship> relationships = new ArrayList<>();
        for (Map<String, Object> item : mapList(value)) {
            relationships.add(new HybridSearchGraphRelationship(
                stringValue(item.get("elementId")),
                stringValue(item.get("type")),
                stringValue(item.get("startNodeElementId")),
                stringValue(item.get("endNodeElementId")),
                objectMap(item.get("properties"))
            ));
        }
        return relationships;
    }

    private List<HybridSearchGraphEvidence> toGraphEvidence(Object value) {
        List<HybridSearchGraphEvidence> evidence = new ArrayList<>();
        for (Map<String, Object> item : mapList(value)) {
            evidence.add(new HybridSearchGraphEvidence(
                stringValue(item.get("evidenceId")),
                stringValue(item.get("factKind")),
                stringValue(item.get("canonicalFactId")),
                stringValue(item.get("sourceDocumentId")),
                stringValue(item.get("sourceChunkId")),
                stringValue(item.get("sourceText")),
                new HybridSearchSourceRange(
                    nullableIntValue(item.get("sourceStart")),
                    nullableIntValue(item.get("sourceEnd")),
                    nullableIntValue(item.get("pageStart")),
                    nullableIntValue(item.get("pageEnd"))
                ),
                stringValue(item.get("processingRunId")),
                stringValue(item.get("effectiveChunkerRevision")),
                HybridSearchCitationKind.GRAPH_PARENT
            ));
        }
        return evidence;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                out.add(stringObjectMap(map));
            }
        }
        return out;
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringObjectMap(map);
        }
        return Map.of();
    }

    private Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : iterable) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Integer nullableIntValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private EmbeddingClient resolveEmbeddingClient() {
        return aiClientResolver.embeddingClient();
    }

    private String hybridSearchCypher(int graphDepth) {
        return """
            UNWIND $candidates AS candidate
            MATCH (chunk:DocumentChunk {id: candidate.chunkId, knowledgeBaseId: $knowledgeBaseId})
            WHERE chunk.kind IS NULL OR chunk.kind = 'CHILD'
            WITH chunk, candidate.score AS score, candidate.rank AS denseRank
            ORDER BY denseRank ASC
            LIMIT $topK
            OPTIONAL MATCH (parent:DocumentChunk {id: chunk.parentChunkId, kind: 'PARENT'})-[:HAS_CHILD]->(chunk)
            WITH chunk, score, denseRank, coalesce(parent, chunk) AS evidenceChunk
            OPTIONAL MATCH (evidenceChunk)-[:HAS_GRAPH_EVIDENCE]->(graphEvidence:GraphExtractionEvidence)
            OPTIONAL MATCH (graphEvidence)-[:ASSERTS_NODE|ASSERTS_FROM|ASSERTS_TO]->(evidenceEntity)
            OPTIONAL MATCH (chunk)-[:MENTIONS]->(legacyMentioned)
            WITH chunk, score, denseRank, evidenceChunk, collect(DISTINCT graphEvidence) AS graphEvidenceNodes,
                 collect(DISTINCT evidenceEntity) + collect(DISTINCT legacyMentioned) AS mentionedNodes
            UNWIND CASE WHEN mentionedNodes = [] THEN [null] ELSE mentionedNodes END AS mentioned
            OPTIONAL MATCH path = (mentioned)-[*0..%d]-(neighbor)
            WITH chunk, score, denseRank, evidenceChunk, graphEvidenceNodes,
                 collect(DISTINCT mentioned) + collect(DISTINCT neighbor) AS entityNodes,
                 collect(DISTINCT relationships(path)) AS relationshipGroups
            WITH chunk, score, denseRank, evidenceChunk, graphEvidenceNodes,
                 [entity IN entityNodes WHERE entity IS NOT NULL | {
                     elementId: elementId(entity),
                     labels: labels(entity),
                     properties: properties(entity)
                 }] AS entities,
                 reduce(rels = [], group IN relationshipGroups | rels + group) AS flatRelationships
            RETURN
                chunk.id AS chunkId,
                chunk.documentId AS documentId,
                chunk.chunkIndex AS chunkIndex,
                chunk.text AS text,
                chunk.metadata AS chunkMetadata,
                chunk.processingRunId AS processingRunId,
                chunk.effectiveChunkerRevision AS effectiveChunkerRevision,
                chunk.sourceStart AS sourceStart,
                chunk.sourceEnd AS sourceEnd,
                chunk.pageStart AS pageStart,
                chunk.pageEnd AS pageEnd,
                chunk.structuralPath AS structuralPath,
                score AS score,
                entities AS entities,
                [evidence IN graphEvidenceNodes WHERE evidence IS NOT NULL
                  AND evidence.sourceChunkId = evidenceChunk.id
                  AND evidence.sourceChunkKind = 'PARENT' | {
                    evidenceId: evidence.id,
                    factKind: evidence.factKind,
                    canonicalFactId: evidence.canonicalFactId,
                    sourceDocumentId: evidence.sourceDocumentId,
                    sourceChunkId: evidence.sourceChunkId,
                    sourceText: CASE WHEN $includeChunkText THEN evidence.sourceChunkText ELSE null END,
                    sourceStart: evidence.sourceStart,
                    sourceEnd: evidence.sourceEnd,
                    pageStart: evidence.pageStart,
                    pageEnd: evidence.pageEnd,
                    processingRunId: evidence.processingRunId,
                    effectiveChunkerRevision: evidence.effectiveChunkerRevision
                  }] AS graphEvidence,
                [rel IN flatRelationships WHERE rel IS NOT NULL | {
                    elementId: elementId(rel),
                    type: type(rel),
                    startNodeElementId: elementId(startNode(rel)),
                    endNodeElementId: elementId(endNode(rel)),
                    properties: properties(rel)
                }] AS relationships
            ORDER BY denseRank ASC
            """.formatted(graphDepth);
    }
}
