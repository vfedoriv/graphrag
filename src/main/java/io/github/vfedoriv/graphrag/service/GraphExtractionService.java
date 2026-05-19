package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.graph.GraphExtractionValidationService;
import io.github.vfedoriv.graphrag.graph.GraphWriteService;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GraphExtractionService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final SchemaParser schemaParser;
    private final ExtractionRunRepository extractionRunRepository;
    private final GraphExtractionValidationService validationService;
    private final GraphWriteService graphWriteService;
    private final ObjectProvider<GraphExtractionClient> graphExtractionClientProvider;
    private final Neo4jClient neo4jClient;

    public GraphExtractionService(
        KnowledgeBaseRepository knowledgeBaseRepository,
        SchemaDefinitionRepository schemaDefinitionRepository,
        SchemaParser schemaParser,
        ExtractionRunRepository extractionRunRepository,
        GraphExtractionValidationService validationService,
        GraphWriteService graphWriteService,
        ObjectProvider<GraphExtractionClient> graphExtractionClientProvider,
        Neo4jClient neo4jClient
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.schemaParser = schemaParser;
        this.extractionRunRepository = extractionRunRepository;
        this.validationService = validationService;
        this.graphWriteService = graphWriteService;
        this.graphExtractionClientProvider = graphExtractionClientProvider;
        this.neo4jClient = neo4jClient;
    }

    public void extract(DocumentUploadNode document, List<DocumentChunkNode> chunks, boolean allowOverwrite) {
        long startNanos = System.nanoTime();
        log.info(
            "Graph extraction starting: documentId={}, knowledgeBaseId={}, chunks={}",
            document.getId(),
            document.getKnowledgeBaseId(),
            chunks.size()
        );
        KnowledgeBaseNode kb = knowledgeBaseRepository.findById(document.getKnowledgeBaseId())
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + document.getKnowledgeBaseId()));
        if (kb.getActiveSchemaId() == null || kb.getActiveSchemaId().isBlank()) {
            throw new IllegalStateException("No active schema for knowledge base: " + kb.getId());
        }
        SchemaDefinitionNode schemaNode = schemaDefinitionRepository.findById(kb.getActiveSchemaId())
            .orElseThrow(() -> new NotFoundException("Schema not found: " + kb.getActiveSchemaId()));
        SchemaDocument schema = schemaParser.parse(schemaNode.getContent());

        GraphExtractionClient client = resolveGraphExtractionClient();
        if (client == null) {
            log.error("Graph extraction client is missing: documentId={}", document.getId());
            throw new IllegalStateException("Graph extraction model is not configured for this profile");
        }
        log.info("Graph extraction client resolved: class={}", client.getClass().getName());

        ExtractionRunNode run = new ExtractionRunNode();
        run.setId(UUID.randomUUID().toString());
        run.setDocumentId(document.getId());
        run.setSchemaId(schemaNode.getId());
        run.setModel("chat:" + schemaNode.getName());
        run.setStatus(ExtractionRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        extractionRunRepository.save(run);
        log.info("Extraction run created: runId={}, schemaId={}, model={}", run.getId(), run.getSchemaId(), run.getModel());
        linkRunToDocument(run.getId(), document.getId());
        try {
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunkNode chunk = chunks.get(i);
                log.info(
                    "Extracting chunk: runId={}, chunkId={}, chunkIndex={}/{} textLength={}",
                    run.getId(),
                    chunk.getId(),
                    i + 1,
                    chunks.size(),
                    chunk.getText() == null ? 0 : chunk.getText().length()
                );
                GraphExtractionResult result = client.extract(schema, chunk.getText());
                log.info(
                    "Chunk extraction returned payload: runId={}, chunkId={}, nodes={}, relationships={}",
                    run.getId(),
                    chunk.getId(),
                    result.nodes().size(),
                    result.relationships().size()
                );
                GraphExtractionResult validatedResult = validationService.validate(result, schema);
                graphWriteService.write(run.getId(), schemaNode.getId(), document.getId(), chunk.getId(), schema, validatedResult);
            }
            run.setStatus(ExtractionRunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            extractionRunRepository.save(run);
            CleanupResult cleanupResult = CleanupResult.zero();
            try {
                cleanupResult = cleanupRunsAfterCompletion(document.getId(), run.getId(), allowOverwrite);
            } catch (Exception cleanupEx) {
                log.error(
                    "Cleanup failed after successful extraction: runId={}, documentId={}, message={}",
                    run.getId(),
                    document.getId(),
                    cleanupEx.getMessage(),
                    cleanupEx
                );
            }
            log.info(
                "Graph extraction completed: runId={}, documentId={}, chunks={}, allowOverwrite={}, deletedRuns={}, deletedRelationships={}, deletedObsoleteExtractedNodes={}, elapsedMs={}",
                run.getId(),
                document.getId(),
                chunks.size(),
                allowOverwrite,
                cleanupResult.deletedRuns(),
                cleanupResult.deletedRelationships(),
                cleanupResult.deletedObsoleteExtractedNodes(),
                LogSanitizer.elapsedMillis(startNanos)
            );
        } catch (Exception ex) {
            run.setStatus(ExtractionRunStatus.FAILED);
            run.setErrorMessage(toNonBlankErrorMessage(ex));
            run.setCompletedAt(Instant.now());
            extractionRunRepository.save(run);
            log.error(
                "Graph extraction failed: runId={}, documentId={}, elapsedMs={}, message={}",
                run.getId(),
                document.getId(),
                LogSanitizer.elapsedMillis(startNanos),
                ex.getMessage(),
                ex
            );
            throw ex;
        }
    }

    private void linkRunToDocument(String runId, String documentId) {
        log.info("Linking extraction run to document: runId={}, documentId={}", runId, documentId);
        neo4jClient.query("""
            MATCH (d:DocumentUpload {id: $documentId})
            MATCH (r:ExtractionRun {id: $runId})
            MERGE (d)-[:HAS_EXTRACTION_RUN]->(r)
            """)
            .bind(documentId).to("documentId")
            .bind(runId).to("runId")
            .run();
        log.info("Extraction run linked to document: runId={}, documentId={}", runId, documentId);
    }

    private GraphExtractionClient resolveGraphExtractionClient() {
        List<GraphExtractionClient> clients = graphExtractionClientProvider.orderedStream().toList();
        if (clients.isEmpty()) {
            return null;
        }
        if (clients.size() == 1) {
            return clients.getFirst();
        }
        return clients.stream()
            .filter(client -> !client.getClass().getName().contains("SpringAi"))
            .findFirst()
            .orElse(clients.getFirst());
    }

    private CleanupResult cleanupRunsAfterCompletion(String documentId, String runId, boolean allowOverwrite) {
        Map<String, Object> cleanupRow = neo4jClient.query("""
            MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(current:ExtractionRun {id: $runId, status: 'COMPLETED'})
            OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(failed:ExtractionRun {status: 'FAILED'})
            WHERE failed.id <> current.id
            OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(completed:ExtractionRun {status: 'COMPLETED'})
            WHERE $allowOverwrite = true AND completed.id <> current.id
            WITH [run IN collect(DISTINCT failed) WHERE run IS NOT NULL] AS failedRuns,
                 [run IN collect(DISTINCT completed) WHERE run IS NOT NULL] AS completedRuns
            WITH failedRuns + completedRuns AS runsToDelete
            WITH runsToDelete, [run IN runsToDelete | run.id] AS runIds
            OPTIONAL MATCH ()-[graphRel]-()
            WHERE graphRel.sourceDocumentId = $documentId AND graphRel.extractionRunId IN runIds
            WITH runsToDelete, runIds, collect(DISTINCT graphRel) AS graphRelationships
            FOREACH (graphRel IN graphRelationships | DELETE graphRel)
            WITH runsToDelete, runIds, size(graphRelationships) AS deletedGraphRelationshipCount
            UNWIND CASE WHEN size(runsToDelete) = 0 THEN [null] ELSE runsToDelete END AS runToDelete
            OPTIONAL MATCH (runToDelete)-[runRel]-()
            WITH runsToDelete, runIds, deletedGraphRelationshipCount, count(DISTINCT runRel) AS deletedRunRelationshipCount
            FOREACH (run IN runsToDelete | DETACH DELETE run)
            WITH size(runsToDelete) AS deletedRuns, deletedGraphRelationshipCount + deletedRunRelationshipCount AS deletedRelationshipCount
            OPTIONAL MATCH (obsoleteNode)
            WHERE deletedRuns > 0
                AND obsoleteNode.sourceDocumentId = $documentId
                AND NOT obsoleteNode:ExtractionRun
                AND NOT obsoleteNode:DocumentUpload
                AND NOT obsoleteNode:DocumentChunk
                AND NOT obsoleteNode:KnowledgeBase
                AND NOT obsoleteNode:SchemaDefinition
                AND NOT (obsoleteNode)<-[:CREATED_NODE]-(:ExtractionRun)
            WITH deletedRuns, deletedRelationshipCount, collect(DISTINCT obsoleteNode) AS obsoleteNodes
            FOREACH (obsoleteNode IN obsoleteNodes | DETACH DELETE obsoleteNode)
            RETURN
                deletedRuns AS deletedRuns,
                deletedRelationshipCount AS deletedRelationships,
                size(obsoleteNodes) AS deletedObsoleteExtractedNodes
            """)
            .bind(documentId).to("documentId")
            .bind(runId).to("runId")
            .bind(allowOverwrite).to("allowOverwrite")
            .fetch()
            .one()
            .orElse(null);
        if (cleanupRow == null) {
            log.warn("Cleanup returned no row: runId={}, documentId={}", runId, documentId);
            return CleanupResult.zero();
        }
        return new CleanupResult(
            toLong(cleanupRow.get("deletedRuns")),
            toLong(cleanupRow.get("deletedRelationships")),
            toLong(cleanupRow.get("deletedObsoleteExtractedNodes"))
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private String toNonBlankErrorMessage(Exception ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return ex.getMessage();
    }

    private record CleanupResult(
        long deletedRuns,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        private static CleanupResult zero() {
            return new CleanupResult(0L, 0L, 0L);
        }
    }
}
