package io.github.vfedoriv.graphrag.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.application.processing.ExtractionRunLifecycle;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.graph.GraphExtractionValidationService;
import io.github.vfedoriv.graphrag.graph.GraphWriteService;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class GraphExtractionServiceTest {

    @Mock
    private ActiveSchemaResolver activeSchemaResolver;
    @Mock
    private ExtractionRunLifecycle extractionRunLifecycle;
    @Mock
    private GraphExtractionValidationService validationService;
    @Mock
    private GraphWriteService graphWriteService;
    @Mock
    private ObjectProvider<GraphExtractionClient> graphExtractionClientProvider;
    @Mock
    private GraphArtifactCleanupService graphArtifactCleanupService;

    @Test
    void keepsCompletedStatusWhenCleanupFails() {
        GraphExtractionClient extractionClient = (schema, chunkText) -> new GraphExtractionResult(List.of(), List.of());
        GraphExtractionService service = serviceWithClient(extractionClient);
        mockActiveSchema();
        when(validationService.validate(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("cleanup boom"))
            .when(graphArtifactCleanupService)
            .cleanupRunsAfterSuccessfulExtraction(eq("doc-1"), anyString(), eq(false));

        service.extract(document(), List.of(chunk()), false);

        verify(extractionRunLifecycle).complete(any());
        verify(extractionRunLifecycle, never()).fail(any(), any());
    }

    @Test
    void storesNonBlankFallbackMessageWhenFailureHasNoMessage() {
        GraphExtractionClient extractionClient = (schema, chunkText) -> {
            throw new IllegalStateException();
        };
        GraphExtractionService service = serviceWithClient(extractionClient);
        mockActiveSchema();

        try {
            service.extract(document(), List.of(chunk()), false);
        } catch (Exception ignored) {
        }

        verify(extractionRunLifecycle).fail(any(), argThat(error -> error instanceof IllegalStateException));
    }

    @Test
    void recordsFailureWhenCompletionCheckpointFailsAfterGraphWrite() {
        GraphExtractionClient extractionClient = (schema, chunkText) -> new GraphExtractionResult(List.of(), List.of());
        GraphExtractionService service = serviceWithClient(extractionClient);
        mockActiveSchema();
        when(validationService.validate(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("completion commit failed"))
            .when(extractionRunLifecycle)
            .complete(any());

        try {
            service.extract(document(), List.of(chunk()), false);
        } catch (Exception ignored) {
        }

        verify(graphWriteService).write(eq("kb-1"), any(), eq("schema-1"), eq("doc-1"), eq("chunk-1"), any(), any());
        verify(extractionRunLifecycle).fail(any(), argThat(error ->
            "completion commit failed".equals(error.getMessage())
        ));
    }

    @Test
    void writesSanitizedValidationResult() {
        GraphExtractionResult raw = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode("Unknown", java.util.Map.of("id", "bad"), 0.4)),
            List.of()
        );
        GraphExtractionResult sanitized = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode("Contract", java.util.Map.of("contractId", "C-1"), 0.9)),
            List.of()
        );
        GraphExtractionClient extractionClient = (schema, chunkText) -> raw;
        GraphExtractionService service = serviceWithClient(extractionClient);
        mockActiveSchema();
        when(validationService.validate(any(), any())).thenReturn(sanitized);

        service.extract(document(), List.of(chunk()), false);

        verify(graphWriteService).write(
            org.mockito.Mockito.eq("kb-1"),
            any(),
            org.mockito.Mockito.eq("schema-1"),
            org.mockito.Mockito.eq("doc-1"),
            org.mockito.Mockito.eq("chunk-1"),
            any(),
            org.mockito.Mockito.eq(sanitized)
        );
    }

    private GraphExtractionService serviceWithClient(GraphExtractionClient extractionClient) {
        when(graphExtractionClientProvider.orderedStream()).thenReturn(java.util.stream.Stream.of(extractionClient));
        lenient()
            .when(graphArtifactCleanupService.cleanupRunsAfterSuccessfulExtraction(anyString(), anyString(), org.mockito.Mockito.anyBoolean()))
            .thenReturn(GraphArtifactCleanupService.ExtractionRunCleanupResult.zero());
        ExtractionRunNode run = new ExtractionRunNode();
        run.setId("run-1");
        run.setDocumentId("doc-1");
        run.setSchemaId("schema-1");
        run.setModel("chat:contracts");
        run.setStatus(ExtractionRunStatus.RUNNING);
        lenient().when(extractionRunLifecycle.start("doc-1", "schema-1", "chat:contracts")).thenReturn(run);
        lenient().when(extractionRunLifecycle.complete(run)).thenAnswer(invocation -> {
            run.setStatus(ExtractionRunStatus.COMPLETED);
            return run;
        });
        return new GraphExtractionService(
            activeSchemaResolver,
            extractionRunLifecycle,
            validationService,
            graphWriteService,
            graphExtractionClientProvider,
            graphArtifactCleanupService,
            TestAiObservationService.noop()
        );
    }

    private void mockActiveSchema() {
        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-1");
        schema.setName("contracts");
        SchemaDocument schemaDocument = new SchemaParser().parse("""
            {
              "name": "contracts",
              "version": 1,
              "nodes": [{"label": "Contract", "key": "contractId", "properties": [{"name": "contractId", "type": "string"}]}],
              "relationships": []
            }
            """);
        when(activeSchemaResolver.resolve("kb-1")).thenReturn(new ActiveSchemaContext("kb-1", "schema-1", schema, schemaDocument));
    }

    private DocumentUploadNode document() {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        return document;
    }

    private DocumentChunkNode chunk() {
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setId("chunk-1");
        chunk.setKnowledgeBaseId("kb-1");
        chunk.setDocumentId("doc-1");
        chunk.setText("hello");
        return chunk;
    }
}
