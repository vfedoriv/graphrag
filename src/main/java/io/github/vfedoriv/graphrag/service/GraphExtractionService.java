package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionValidationService;
import io.github.vfedoriv.graphrag.graph.GraphWriteService;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class GraphExtractionService {

    private static final Logger log = LoggerFactory.getLogger(GraphExtractionService.class);

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
        log.info(
            "Graph extraction starting: documentId={}, knowledgeBaseId={}, chunks={}",
            document.getId(),
            document.getKnowledgeBaseId(),
            chunks.size()
        );
        var kb = knowledgeBaseRepository.findById(document.getKnowledgeBaseId())
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + document.getKnowledgeBaseId()));
        if (kb.getActiveSchemaId() == null || kb.getActiveSchemaId().isBlank()) {
            throw new IllegalStateException("No active schema for knowledge base: " + kb.getId());
        }
        var schemaNode = schemaDefinitionRepository.findById(kb.getActiveSchemaId())
            .orElseThrow(() -> new NotFoundException("Schema not found: " + kb.getActiveSchemaId()));
        var schema = schemaParser.parse(schemaNode.getContent());

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
                var chunk = chunks.get(i);
                log.info(
                    "Extracting chunk: runId={}, chunkId={}, chunkIndex={}/{} textLength={}",
                    run.getId(),
                    chunk.getId(),
                    i + 1,
                    chunks.size(),
                    chunk.getText() == null ? 0 : chunk.getText().length()
                );
                var result = client.extract(schema, chunk.getText());
                log.info(
                    "Chunk extraction returned payload: runId={}, chunkId={}, nodes={}, relationships={}",
                    run.getId(),
                    chunk.getId(),
                    result.nodes() == null ? 0 : result.nodes().size(),
                    result.relationships() == null ? 0 : result.relationships().size()
                );
                var validatedResult = validationService.validate(result, schema);
                graphWriteService.write(run.getId(), schemaNode.getId(), document.getId(), chunk.getId(), schema, validatedResult);
            }
            run.setStatus("COMPLETED");
            run.setCompletedAt(Instant.now());
            extractionRunRepository.save(run);
            log.info("Graph extraction completed: runId={}, documentId={}", run.getId(), document.getId());
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setErrorMessage(ex.getMessage());
            run.setCompletedAt(Instant.now());
            extractionRunRepository.save(run);
            log.error(
                "Graph extraction failed: runId={}, documentId={}, message={}",
                run.getId(),
                document.getId(),
                ex.getMessage(),
                ex
            );
            throw ex;
        }
    }

    private void linkRunToDocument(String runId, String documentId) {
        neo4jClient.query("""
            MATCH (d:DocumentUpload {id: $documentId})
            MATCH (r:ExtractionRun {id: $runId})
            MERGE (d)-[:HAS_EXTRACTION_RUN]->(r)
            """)
            .bind(documentId).to("documentId")
            .bind(runId).to("runId")
            .run();
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
}
