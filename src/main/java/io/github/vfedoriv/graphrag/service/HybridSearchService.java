package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.dto.HybridSearchGraphContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphEntity;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphRelationship;
import io.github.vfedoriv.graphrag.dto.HybridSearchHit;
import io.github.vfedoriv.graphrag.dto.HybridSearchRequest;
import io.github.vfedoriv.graphrag.dto.HybridSearchResponse;
import io.github.vfedoriv.graphrag.dto.HybridSearchSource;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HybridSearchService {

    private final RuntimeSettingsService runtimeSettingsService;
    private final ProfileScopedAiClientResolver aiClientResolver;
    private final Neo4jClient neo4jClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingSpacePolicy embeddingSpacePolicy;
    private final EmbeddingSpaceIndexService embeddingSpaceIndexService;

    @Autowired
    public HybridSearchService(
        RuntimeSettingsService runtimeSettingsService,
        ProfileScopedAiClientResolver aiClientResolver,
        Neo4jClient neo4jClient,
        KnowledgeBaseService knowledgeBaseService,
        EmbeddingSpacePolicy embeddingSpacePolicy,
        EmbeddingSpaceIndexService embeddingSpaceIndexService
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.aiClientResolver = aiClientResolver;
        this.neo4jClient = neo4jClient;
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingSpacePolicy = embeddingSpacePolicy;
        this.embeddingSpaceIndexService = embeddingSpaceIndexService;
    }

    public HybridSearchService(
        RuntimeSettingsService runtimeSettingsService,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        Neo4jClient neo4jClient,
        KnowledgeBaseService knowledgeBaseService,
        io.github.vfedoriv.graphrag.repository.DocumentChunkRepository documentChunkRepository
    ) {
        this(
            runtimeSettingsService,
            new ProfileScopedAiClientResolver(
                embeddingClientProvider,
                new EmptyObjectProvider<>(),
                new EmptyObjectProvider<>()
            ),
            neo4jClient,
            knowledgeBaseService,
            new EmbeddingSpacePolicy(documentChunkRepository),
            new EmbeddingSpaceIndexService(neo4jClient)
        );
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
            LogSanitizer.length(request.query()),
            topK,
            candidateCount,
            graphDepth,
            includeChunkText
        );

        AiProfileNode activeProfile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        EmbeddingSpace embeddingSpace = embeddingSpacePolicy.spaceFor(activeProfile);
        embeddingSpacePolicy.requireCompatible(knowledgeBaseId, activeProfile);
        EmbeddingClient embeddingClient = resolveEmbeddingClient();
        if (embeddingClient == null) {
            throw new IllegalStateException("Embedding model is not configured for hybrid search");
        }
        if (!embeddingSpacePolicy.hasEmbeddedChunks(knowledgeBaseId)) {
            return emptyResponse(request, topK, graphDepth, includeChunkText, startNanos, knowledgeBaseId);
        }
        List<List<Double>> vectors = AiProfileContext.withProfile(activeProfile.getId(), () -> embeddingClient.embed(List.of(request.query())));
        if (vectors.size() != 1) {
            throw new IllegalStateException("Embedding response size mismatch for hybrid search query");
        }
        embeddingSpaceIndexService.ensureIndex(knowledgeBaseId, embeddingSpace);

        List<Map<String, Object>> rows = new ArrayList<>(neo4jClient.query(hybridSearchCypher(graphDepth))
            .bind(embeddingSpaceIndexService.indexName(knowledgeBaseId, embeddingSpace.id())).to("indexName")
            .bind(candidateCount).to("candidateCount")
            .bind(vectors.getFirst()).to("queryVector")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(topK).to("topK")
            .fetch()
            .all());
        List<HybridSearchHit> hits = rows.stream()
            .map(row -> toHit(row, includeChunkText))
            .toList();
        HybridSearchResponse response = new HybridSearchResponse(
            request.query(),
            topK,
            graphDepth,
            includeChunkText,
            hits,
            hits.size(),
            LogSanitizer.elapsedMillis(startNanos)
        );
        log.info(
            "Hybrid search completed: knowledgeBaseId={}, hitCount={}, executionTimeMs={}",
            knowledgeBaseId,
            response.hitCount(),
            response.executionTimeMs()
        );
        return response;
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
            LogSanitizer.elapsedMillis(startNanos)
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

    private HybridSearchHit toHit(Map<String, Object> row, boolean includeChunkText) {
        String chunkId = stringValue(row.get("chunkId"));
        String documentId = stringValue(row.get("documentId"));
        int chunkIndex = intValue(row.get("chunkIndex"));
        double score = doubleValue(row.get("score"));
        String text = includeChunkText ? stringValue(row.get("text")) : null;
        HybridSearchSource source = new HybridSearchSource(
            documentId,
            stringValue(row.get("originalFilename")),
            stringValue(row.get("contentType")),
            longValue(row.get("sizeBytes")),
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
                toRelationships(row.get("relationships"))
            )
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

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private EmbeddingClient resolveEmbeddingClient() {
        return aiClientResolver.embeddingClient();
    }

    private String hybridSearchCypher(int graphDepth) {
        return """
            CALL db.index.vector.queryNodes($indexName, $candidateCount, $queryVector) YIELD node AS chunk, score
            MATCH (document:DocumentUpload {knowledgeBaseId: $knowledgeBaseId})-[:HAS_CHUNK]->(chunk)
            WITH document, chunk, score
            ORDER BY score DESC
            LIMIT $topK
            OPTIONAL MATCH (chunk)-[:MENTIONS]->(mentioned)
            OPTIONAL MATCH path = (mentioned)-[*0..%d]-(neighbor)
            WITH document, chunk, score, collect(DISTINCT mentioned) + collect(DISTINCT neighbor) AS entityNodes, collect(DISTINCT relationships(path)) AS relationshipGroups
            WITH document, chunk, score,
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
                document.originalFilename AS originalFilename,
                document.contentType AS contentType,
                document.sizeBytes AS sizeBytes,
                score AS score,
                entities AS entities,
                [rel IN flatRelationships WHERE rel IS NOT NULL | {
                    elementId: elementId(rel),
                    type: type(rel),
                    startNodeElementId: elementId(startNode(rel)),
                    endNodeElementId: elementId(endNode(rel)),
                    properties: properties(rel)
                }] AS relationships
            ORDER BY score DESC
            """.formatted(graphDepth);
    }
}
