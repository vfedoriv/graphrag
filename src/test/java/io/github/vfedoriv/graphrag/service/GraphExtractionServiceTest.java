package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.graph.GraphExtractionValidationService;
import io.github.vfedoriv.graphrag.graph.GraphWriteService;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;

@ExtendWith(MockitoExtension.class)
class GraphExtractionServiceTest {

    @Mock
    private ActiveSchemaResolver activeSchemaResolver;
    @Mock
    private ExtractionRunRepository extractionRunRepository;
    @Mock
    private GraphExtractionValidationService validationService;
    @Mock
    private GraphWriteService graphWriteService;
    @Mock
    private ObjectProvider<GraphExtractionClient> graphExtractionClientProvider;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;
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

        ArgumentCaptor<ExtractionRunNode> runCaptor = ArgumentCaptor.forClass(ExtractionRunNode.class);
        verify(extractionRunRepository, org.mockito.Mockito.atLeast(2)).save(runCaptor.capture());
        List<ExtractionRunNode> savedRuns = runCaptor.getAllValues();
        ExtractionRunNode finalSave = savedRuns.getLast();
        assertThat(finalSave.getStatus()).isEqualTo(ExtractionRunStatus.COMPLETED);
        verify(extractionRunRepository, never()).save(argThat(run -> run.getStatus() == ExtractionRunStatus.FAILED));
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

        verify(extractionRunRepository, org.mockito.Mockito.atLeastOnce()).save(argThat(run ->
            run.getStatus() == ExtractionRunStatus.FAILED
                && run.getErrorMessage() != null
                && !run.getErrorMessage().isBlank()
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
        return new GraphExtractionService(
            activeSchemaResolver,
            extractionRunRepository,
            validationService,
            graphWriteService,
            graphExtractionClientProvider,
            neo4jClient,
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
        chunk.setText("hello");
        return chunk;
    }
}
