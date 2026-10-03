package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.schemas.reprocessing.api.SchemaReprocessingPlanController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.CreatePlanRequest;
import io.github.vfedoriv.graphrag.schemas.reprocessing.api.model.SchemaReprocessingDtos.StartPlanResponse;
import io.github.vfedoriv.graphrag.bootstrap.http.GlobalExceptionHandler;
import io.github.vfedoriv.graphrag.schemas.reprocessing.application.SchemaReprocessingPlanService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SchemaReprocessingPlanControllerTest {

    private SchemaReprocessingPlanService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SchemaReprocessingPlanService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SchemaReprocessingPlanController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        when(service.create(eq("kb-1"), any(CreatePlanRequest.class)))
            .thenReturn(new StartPlanResponse(
                "plan-1",
                SchemaReprocessingPlanStatus.QUEUED,
                "/api/v1/knowledge-bases/kb-1/reprocessing-plans/plan-1"
            ));
    }

    @Test
    void acceptsMigrationBodyWithoutSchemaOnlyAllDocumentsProperty() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans", "kb-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "CHUNK_STRATEGY_MIGRATION",
                      "selection": "OUTDATED_STRATEGY",
                      "processingOptions": null,
                      "expectedChunkerRevision": "chunker-current"
                    }
                    """))
            .andExpect(status().isAccepted());

        ArgumentCaptor<CreatePlanRequest> requestCaptor = ArgumentCaptor.forClass(CreatePlanRequest.class);
        verify(service).create(eq("kb-1"), requestCaptor.capture());
        CreatePlanRequest request = requestCaptor.getValue();
        assertThat(request.allDocuments()).isNull();
        assertThat(request.reason()).isEqualTo(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        assertThat(request.selection()).isEqualTo(ChunkReprocessingSelection.OUTDATED_STRATEGY);
        assertThat(request.processingOptions()).isNull();
        assertThat(request.expectedChunkerRevision()).isEqualTo("chunker-current");
    }

    @Test
    void keepsMigrationSelectionAuthoritativeForNullAndLegacyAllDocumentsValues() throws Exception {
        List<String> allDocumentsValues = List.of("null", "false", "true");

        for (String allDocumentsValue : allDocumentsValues) {
            mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans", "kb-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "CHUNK_STRATEGY_MIGRATION",
                          "selection": "DOCUMENT_IDS",
                          "documentIds": ["doc-1"],
                          "processingOptions": null,
                          "expectedChunkerRevision": "chunker-current",
                          "allDocuments": %s
                        }
                        """.formatted(allDocumentsValue)))
                .andExpect(status().isAccepted());
        }

        ArgumentCaptor<CreatePlanRequest> requestCaptor = ArgumentCaptor.forClass(CreatePlanRequest.class);
        verify(service, times(3)).create(eq("kb-1"), requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
            .extracting(CreatePlanRequest::selection)
            .containsOnly(ChunkReprocessingSelection.DOCUMENT_IDS);
        assertThat(requestCaptor.getAllValues())
            .extracting(CreatePlanRequest::documentIds)
            .containsOnly(List.of("doc-1"));
        assertThat(requestCaptor.getAllValues())
            .extracting(CreatePlanRequest::allDocuments)
            .containsExactly(null, false, true);
    }
}
