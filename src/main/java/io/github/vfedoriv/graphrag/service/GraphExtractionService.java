package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
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

    public void extract(DocumentUploadNode document, List<DocumentChunkNode> chunks) {
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
        run.setStatus("RUNNING");
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
                    result.nodes() == null ? 0 : result.nodes().size(),
                    result.relationships() == null ? 0 : result.relationships().size()
                );
                GraphExtractionResult validatedResult = validationService.validate(result, schema);
                graphWriteService.write(run.getId(), schemaNode.getId(), document.getId(), chunk.getId(), schema, validatedResult);
            }
            run.setStatus("COMPLETED");
            run.setCompletedAt(Instant.now());
            extractionRunRepository.save(run);
            Map<String, Object> cleanupRow = cleanupFailedRunsAfterCompletion(document.getId(), run.getId());
            long deletedFailedRuns = toLong(cleanupRow.get("deletedRuns"));
            long deletedRelationships = toLong(cleanupRow.get("deletedRelationships"));
            long deletedOrphanNodes = toLong(cleanupRow.get("deletedOrphanNodes"));
            log.info(
                "Graph extraction completed: runId={}, documentId={}, chunks={}, deletedFailedRuns={}, deletedRelationships={}, deletedOrphanNodes={}, elapsedMs={}",
                run.getId(),
                document.getId(),
                chunks.size(),
                deletedFailedRuns,
                deletedRelationships,
                deletedOrphanNodes,
                LogSanitizer.elapsedMillis(startNanos)
            );
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setErrorMessage(ex.getMessage());
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

    private Map<String, Object> cleanupFailedRunsAfterCompletion(String documentId, String runId) {
        return neo4jClient.query("""
            MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(current:ExtractionRun {id: $runId, status: 'COMPLETED'})
            OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(failed:ExtractionRun {status: 'FAILED'})
            WHERE failed.id <> current.id
            OPTIONAL MATCH (failed)-[failedRel]-()
            OPTIONAL MATCH (failed)-[:CREATED_NODE]->(candidateNode)
            WITH
                collect(DISTINCT failed) AS failedRunsRaw,
                count(DISTINCT failedRel) AS failedRelationshipCount,
                collect(DISTINCT candidateNode) AS candidateNodesRaw
            WITH
                [run IN failedRunsRaw WHERE run IS NOT NULL] AS failedRuns,
                failedRelationshipCount,
                [node IN candidateNodesRaw WHERE node IS NOT NULL] AS candidateNodes
            FOREACH (run IN failedRuns | DETACH DELETE run)
            WITH size(failedRuns) AS deletedRuns, failedRelationshipCount, candidateNodes
            UNWIND candidateNodes AS candidateNode
            WITH deletedRuns, failedRelationshipCount, candidateNode
            WHERE NOT (candidateNode)--()
            WITH deletedRuns, failedRelationshipCount, collect(DISTINCT candidateNode) AS orphanNodes
            FOREACH (orphanNode IN orphanNodes | DETACH DELETE orphanNode)
            RETURN
                deletedRuns AS deletedRuns,
                failedRelationshipCount AS deletedRelationships,
                size(orphanNodes) AS deletedOrphanNodes
            """)
            .bind(documentId).to("documentId")
            .bind(runId).to("runId")
            .fetch()
            .one()
            .orElse(Map.of());
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
