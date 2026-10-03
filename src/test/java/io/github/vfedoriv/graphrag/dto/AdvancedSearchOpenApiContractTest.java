package io.github.vfedoriv.graphrag.dto;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchResultDtos;

import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchReadinessDtos;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchRunDtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.search.runs.api.model.AdvancedSearchResultDtos.AdvancedSearchResultV1;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedSearchOpenApiContractTest {
    @Test
    void exposesTypedVersionOneResultAndReadinessSchemas() {
        Map<String, Schema> resultSchemas = ModelConverters.getInstance()
            .readAll(AdvancedSearchRunDtos.ResultResponse.class);

        assertThat(resultSchemas).containsKeys(
            "ResultResponse", "AdvancedSearchResultV1", "AnswerResponse", "EvidenceResponse",
            "GraphFactResponse", "PipelineDiagnosticsResponse", "SourceMetadataDiagnosticsResponse");
        assertThat(resultSchemas.get("AdvancedSearchResultV1").getProperties())
            .containsKeys("payloadVersion", "answer", "evidence", "contexts", "graphFacts", "answerDiagnostics", "diagnostics");
        assertThat(resultSchemas.get("EvidenceResponse").getProperties())
            .containsKeys("sourceFilename", "sourceContentType", "sourceDisplayLabel");

        Map<String, Schema> readinessSchemas = ModelConverters.getInstance()
            .readAll(AdvancedSearchReadinessDtos.ReadinessResponse.class);
        assertThat(readinessSchemas.get("ReadinessResponse").getProperties())
            .containsKeys("ready", "profileId", "profileRevision", "graphBranchAvailable", "embeddedCorpusPresent",
                "blockers", "informational");

        Map<String, Schema> detailSchemas = ModelConverters.getInstance()
            .readAll(AdvancedSearchRunDtos.RunDetailResponse.class);
        assertThat(detailSchemas.get("RunDetailResponse").getProperties())
            .containsKeys("query", "maximumEvidence", "includeEvidenceText", "status", "links");
        Map<String, Schema> summarySchemas = ModelConverters.getInstance()
            .readAll(AdvancedSearchRunDtos.RunSummaryResponse.class);
        assertThat(summarySchemas.get("RunSummaryResponse").getProperties())
            .containsKeys("queryPreview", "maximumEvidence", "includeEvidenceText", "status", "links")
            .doesNotContainKey("query");
    }

    @Test
    void serializesDocumentedRequestAndRunFieldsWithTheCurrentNames() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AdvancedSearchRunDtos.CreateRequest request = new AdvancedSearchRunDtos.CreateRequest(
            "When does the agreement renew?", 10, true);
        AdvancedSearchResultV1 result = new AdvancedSearchResultV1(
            1, null, List.of(), List.of(), List.of(), null, null);

        String requestJson = objectMapper.writeValueAsString(request);
        String resultJson = objectMapper.writeValueAsString(result);

        assertThat(requestJson).contains("\"maximumEvidence\":10").doesNotContain("maxEvidence");
        assertThat(resultJson).contains("\"payloadVersion\":1", "\"evidence\":[]", "\"contexts\":[]");

        String readinessJson = objectMapper.writeValueAsString(new AdvancedSearchReadinessDtos.ReadinessResponse(
            "kb-demo", false, "default", 3L, false, false,
            List.of(new AdvancedSearchReadinessDtos.ReadinessIssue("CHAT_CONFIGURATION_UNAVAILABLE", "unusable")),
            List.of()));
        assertThat(readinessJson).contains("\"ready\":false", "CHAT_CONFIGURATION_UNAVAILABLE", "\"blockers\"");
    }
}
