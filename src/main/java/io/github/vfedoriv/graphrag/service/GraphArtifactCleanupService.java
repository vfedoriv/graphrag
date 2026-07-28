package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GraphArtifactCleanupService {

    private final Neo4jClient neo4jClient;
    private final ExtractionRunRepository extractionRunRepository;

    public GraphArtifactCleanupService(
        Neo4jClient neo4jClient,
        ExtractionRunRepository extractionRunRepository
    ) {
        this.neo4jClient = neo4jClient;
        this.extractionRunRepository = extractionRunRepository;
    }

    public DocumentArtifactCleanupResult cleanupDocumentArtifacts(String documentId) {
        List<String> runIds = extractionRunIds(documentId);
        EvidenceCleanupResult evidenceResult = cleanupEvidence(documentId, runIds, true);
        long deletedLegacyFacts = cleanupLegacyFacts(documentId, runIds);
        CleanupCounts cleanupCounts = deleteDocumentInfrastructure(documentId);
        long deletedUnsupportedFacts = deleteUnsupportedCanonicalFacts(evidenceResult.canonicalFactIds());
        return new DocumentArtifactCleanupResult(
            cleanupCounts.deletedChunks(),
            cleanupCounts.deletedProcessingRuns(),
            cleanupCounts.deletedRuns(),
            evidenceResult.deletedEvidence(),
            deletedLegacyFacts + deletedUnsupportedFacts,
            deletedUnsupportedFacts
        );
    }

    public ExtractionRunCleanupResult cleanupRunsAfterSuccessfulExtraction(String documentId, String runId, boolean allowOverwrite) {
        List<String> runIds = staleExtractionRunIds(documentId, runId, allowOverwrite);
        return cleanupExtractionRuns(documentId, runIds);
    }

    public ExtractionRunCleanupResult cleanupExtractionRuns(String documentId, List<String> runIds) {
        if (runIds.isEmpty()) {
            return ExtractionRunCleanupResult.zero();
        }
        EvidenceCleanupResult evidenceResult = cleanupEvidence(documentId, runIds, false);
        long deletedLegacyFacts = cleanupLegacyFacts(documentId, runIds);
        long deletedRuns = deleteRuns(runIds);
        long deletedUnsupportedFacts = deleteUnsupportedCanonicalFacts(evidenceResult.canonicalFactIds());
        return new ExtractionRunCleanupResult(
            deletedRuns,
            evidenceResult.deletedEvidence(),
            deletedLegacyFacts + deletedUnsupportedFacts,
            deletedUnsupportedFacts
        );
    }

    private List<String> extractionRunIds(String documentId) {
        return extractionRunRepository.findIdsByDocumentId(documentId);
    }

    private List<String> staleExtractionRunIds(String documentId, String runId, boolean allowOverwrite) {
        ExtractionRunNode current = extractionRunRepository.findById(runId).orElse(null);
        if (current == null
            || !documentId.equals(current.getDocumentId())
            || current.getStatus() != ExtractionRunStatus.COMPLETED) {
            return List.of();
        }
        List<String> runIds = new ArrayList<>(
            extractionRunRepository.findIdsByDocumentIdAndStatus(documentId, ExtractionRunStatus.FAILED)
        );
        if (allowOverwrite) {
            runIds.addAll(
                extractionRunRepository.findIdsByDocumentIdAndStatus(documentId, ExtractionRunStatus.COMPLETED)
            );
        }
        runIds.removeIf(runId::equals);
        return runIds.stream().distinct().toList();
    }

    private EvidenceCleanupResult cleanupEvidence(String documentId, List<String> runIds, boolean entireDocument) {
        List<String> canonicalFactIds = entireDocument
            ? evidenceFactIdsForDocument(documentId)
            : evidenceFactIdsForRuns(runIds);
        Map<String, Object> cleanupRow = entireDocument
            ? deleteEvidenceForDocument(documentId)
            : deleteEvidenceForRuns(runIds);
        return new EvidenceCleanupResult(
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedEvidence")),
            canonicalFactIds
        );
    }

    private List<String> evidenceFactIdsForDocument(String documentId) {
        return List.copyOf(neo4jClient.query("""
            MATCH (e:GraphExtractionEvidence {sourceDocumentId: $documentId})
            RETURN DISTINCT e.canonicalFactId AS canonicalFactId
            """)
            .bind(documentId).to("documentId")
            .fetchAs(String.class)
            .all());
    }

    private List<String> evidenceFactIdsForRuns(List<String> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(neo4jClient.query("""
            MATCH (e:GraphExtractionEvidence)
            WHERE e.extractionRunId IN $runIds
            RETURN DISTINCT e.canonicalFactId AS canonicalFactId
            """)
            .bind(runIds).to("runIds")
            .fetchAs(String.class)
            .all());
    }

    private Map<String, Object> deleteEvidenceForDocument(String documentId) {
        return neo4jClient.query("""
            MATCH (e:GraphExtractionEvidence {sourceDocumentId: $documentId})
            WITH collect(DISTINCT e) AS evidence
            FOREACH (e IN evidence | DETACH DELETE e)
            RETURN size(evidence) AS deletedEvidence
            """)
            .bind(documentId).to("documentId")
            .fetch()
            .one()
            .orElse(Map.of());
    }

    private Map<String, Object> deleteEvidenceForRuns(List<String> runIds) {
        if (runIds.isEmpty()) {
            return Map.of("deletedEvidence", 0L);
        }
        return neo4jClient.query("""
            MATCH (e:GraphExtractionEvidence)
            WHERE e.extractionRunId IN $runIds
            WITH collect(DISTINCT e) AS evidence
            FOREACH (e IN evidence | DETACH DELETE e)
            RETURN size(evidence) AS deletedEvidence
            """)
            .bind(runIds).to("runIds")
            .fetch()
            .one()
            .orElse(Map.of());
    }

    private long cleanupLegacyFacts(String documentId, List<String> runIds) {
        long deletedRelationships = executeCount("""
            MATCH ()-[legacyRelationship]->()
            WHERE legacyRelationship.sourceDocumentId = $documentId
                AND (size($runIds) = 0 OR legacyRelationship.extractionRunId IN $runIds)
                AND NOT EXISTS {
                    MATCH (:GraphExtractionEvidence {canonicalFactId: legacyRelationship.id})
                }
            DELETE legacyRelationship
            RETURN count(legacyRelationship) AS count
            """, documentId, runIds, "runIds");
        long deletedNodes = executeCount("""
            MATCH (legacyNode)
            WHERE legacyNode.sourceDocumentId = $documentId
                AND (size($runIds) = 0 OR legacyNode.extractionRunId IN $runIds)
                AND NOT legacyNode:ExtractionRun
                AND NOT legacyNode:DocumentProcessingRun
                AND NOT legacyNode:DocumentUpload
                AND NOT legacyNode:DocumentChunk
                AND NOT legacyNode:KnowledgeBase
                AND NOT legacyNode:SchemaDefinition
                AND NOT EXISTS {
                    MATCH (:GraphExtractionEvidence {canonicalFactId: legacyNode.id})
                }
            DETACH DELETE legacyNode
            RETURN count(legacyNode) AS count
            """, documentId, runIds, "runIds");
        return deletedRelationships + deletedNodes;
    }

    private CleanupCounts deleteDocumentInfrastructure(String documentId) {
        Map<String, Object> cleanupRow = neo4jClient.query("""
            MATCH (document:DocumentUpload {id: $documentId})
            OPTIONAL MATCH (document)-[:HAS_CHUNK]->(chunk:DocumentChunk)
            WITH document, [entry IN collect(DISTINCT chunk) WHERE entry IS NOT NULL] AS chunks
            FOREACH (chunk IN chunks | DETACH DELETE chunk)
            WITH document, size(chunks) AS deletedChunks
            OPTIONAL MATCH (document)-[:HAS_PROCESSING_RUN]->(processingRun:DocumentProcessingRun)
            WITH document, deletedChunks, [entry IN collect(DISTINCT processingRun) WHERE entry IS NOT NULL] AS processingRuns
            FOREACH (processingRun IN processingRuns | DETACH DELETE processingRun)
            WITH document, deletedChunks, size(processingRuns) AS deletedProcessingRuns
            OPTIONAL MATCH (document)-[:HAS_EXTRACTION_RUN]->(run:ExtractionRun)
            WITH deletedChunks, deletedProcessingRuns, [entry IN collect(DISTINCT run) WHERE entry IS NOT NULL] AS runs
            FOREACH (run IN runs | DETACH DELETE run)
            RETURN deletedChunks AS deletedChunks, deletedProcessingRuns AS deletedProcessingRuns, size(runs) AS deletedRuns
            """)
            .bind(documentId).to("documentId")
            .fetch()
            .one()
            .orElse(Map.of());
        return new CleanupCounts(
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedChunks")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedProcessingRuns")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRuns"))
        );
    }

    private long deleteRuns(List<String> runIds) {
        if (runIds.isEmpty()) {
            return 0L;
        }
        return executeCount("""
            MATCH (run:ExtractionRun)
            WHERE run.id IN $runIds
            DETACH DELETE run
            RETURN count(run) AS count
            """, null, runIds, "runIds");
    }

    private long deleteUnsupportedCanonicalFacts(List<String> canonicalFactIds) {
        if (canonicalFactIds.isEmpty()) {
            return 0L;
        }
        long deletedRelationships = executeCount("""
            MATCH ()-[canonicalRelationship]->()
            WHERE canonicalRelationship.id IN $canonicalFactIds
                AND NOT EXISTS {
                    MATCH (:GraphExtractionEvidence {canonicalFactId: canonicalRelationship.id})
                }
            DELETE canonicalRelationship
            RETURN count(canonicalRelationship) AS count
            """, null, canonicalFactIds, "canonicalFactIds");
        long deletedNodes = executeCount("""
            MATCH (canonicalNode)
            WHERE canonicalNode.id IN $canonicalFactIds
                AND canonicalNode.id STARTS WITH 'node:'
                AND NOT EXISTS {
                    MATCH (:GraphExtractionEvidence {canonicalFactId: canonicalNode.id})
                }
            DETACH DELETE canonicalNode
            RETURN count(canonicalNode) AS count
            """, null, canonicalFactIds, "canonicalFactIds");
        return deletedRelationships + deletedNodes;
    }

    private long executeCount(String cypher, String documentId, List<String> ids, String idsParameterName) {
        if (documentId != null) {
            Map<String, Object> row = neo4jClient.query(cypher)
                .bind(ids).to(idsParameterName)
                .bind(documentId).to("documentId")
                .fetch()
                .one()
                .orElse(Map.of());
            return GraphExtractionCleanupSupport.toLong(row.get("count"));
        }
        Map<String, Object> row = neo4jClient.query(cypher)
            .bind(ids).to(idsParameterName)
            .fetch()
            .one()
            .orElse(Map.of());
        return GraphExtractionCleanupSupport.toLong(row.get("count"));
    }

    static DocumentArtifactCleanupResult documentCleanupResult(Map<String, Object> cleanupRow) {
        if (cleanupRow == null) {
            return DocumentArtifactCleanupResult.zero();
        }
        return new DocumentArtifactCleanupResult(
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedChunks")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedProcessingRuns")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRuns")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedGraphEvidence")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRelationships")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedObsoleteExtractedNodes"))
        );
    }

    static ExtractionRunCleanupResult extractionRunCleanupResult(Map<String, Object> cleanupRow) {
        if (cleanupRow == null) {
            return ExtractionRunCleanupResult.zero();
        }
        return new ExtractionRunCleanupResult(
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRuns")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedGraphEvidence")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRelationships")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedObsoleteExtractedNodes"))
        );
    }

    public record DocumentArtifactCleanupResult(
        long deletedChunks,
        long deletedProcessingRuns,
        long deletedRuns,
        long deletedGraphEvidence,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        public static DocumentArtifactCleanupResult zero() {
            return new DocumentArtifactCleanupResult(0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public record ExtractionRunCleanupResult(
        long deletedRuns,
        long deletedGraphEvidence,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        public static ExtractionRunCleanupResult zero() {
            return new ExtractionRunCleanupResult(0L, 0L, 0L, 0L);
        }
    }

    private record EvidenceCleanupResult(long deletedEvidence, List<String> canonicalFactIds) {
    }

    private record CleanupCounts(long deletedChunks, long deletedProcessingRuns, long deletedRuns) {
    }
}
