package io.github.vfedoriv.graphrag.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AdvisoryExecutionStatus;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.CandidatePageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationOutcomePageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.MetricApplicability;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.QuestionCoverage;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.SourceOutcomePageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationRunPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationIneligibilityReason;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationReadiness;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanItemPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.PlanPageResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaDraftOpenApiContractTest {
    @Test
    void exposesStableSpecializedPageSchemas() {
        assertPageSchema("SchemaDraftCandidatePage", CandidatePageResponse.class);
        assertPageSchema("SchemaDraftAnalysisSourceOutcomePage", SourceOutcomePageResponse.class);
        assertPageSchema("SchemaDraftEvaluationOutcomePage", EvaluationOutcomePageResponse.class);
        assertPageSchema("SchemaReprocessingPlanItemPage", PlanItemPageResponse.class);
        assertPageSchema("SchemaDraftAnalysisRunPage", AnalysisRunPageResponse.class);
        assertPageSchema("SchemaDraftEvaluationRunPage", EvaluationRunPageResponse.class);
        assertPageSchema("SchemaDraftEvaluationEligibleDocumentPage", EvaluationEligibleDocumentPageResponse.class);
        assertPageSchema("SchemaReprocessingPlanPage", PlanPageResponse.class);
    }

    @Test
    void exposesDocumentedMetricAndAdvisoryEnumValues() {
        assertThat(List.of(MetricApplicability.values())).extracting(Enum::name)
            .containsExactly("APPLICABLE", "NOT_APPLICABLE");
        assertThat(List.of(AdvisoryExecutionStatus.values())).extracting(Enum::name)
            .containsExactly("NOT_REQUESTED", "COMPLETED", "COMPLETED_WITHOUT_MODEL_JUDGMENT", "FAILED");
        assertThat(List.of(QuestionCoverage.values())).extracting(Enum::name)
            .containsExactly("SUPPORTED", "PARTIALLY_SUPPORTED", "UNSUPPORTED", "UNASSESSED");

        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(SchemaDraftDtos.EvaluationRunResponse.class);
        assertThat(schemas).containsKeys("EvaluationMetricsResponse", "AdvisoryAssessmentResponse",
            "EvaluationReproducibilityResponse", "RateMetricResponse", "QuestionAssessmentResponse",
            "SchemaDraftEvaluationOutcomePage");
        assertThat(((Schema) schemas.get("RateMetricResponse").getProperties().get("applicability")).getEnum())
            .containsExactly("APPLICABLE", "NOT_APPLICABLE");
        assertThat(((Schema) schemas.get("AdvisoryAssessmentResponse").getProperties().get("status")).getEnum())
            .containsExactly("NOT_REQUESTED", "COMPLETED", "COMPLETED_WITHOUT_MODEL_JUDGMENT", "FAILED");
        assertThat(((Schema) schemas.get("QuestionAssessmentResponse").getProperties().get("coverage")).getEnum())
            .containsExactly("SUPPORTED", "PARTIALLY_SUPPORTED", "UNSUPPORTED", "UNASSESSED");
        assertThat(List.of(EvaluationReadiness.values())).extracting(Enum::name)
            .containsExactly("READY", "NOT_READY");
        assertThat(List.of(EvaluationIneligibilityReason.values())).extracting(Enum::name)
            .containsExactly("ACTIVE_DISCOVERY_EVIDENCE", "DRAFT_ANALYSIS_REQUIRED");

        Map<String, Schema> eligibilitySchemas = ModelConverters.getInstance()
            .readAll(EvaluationEligibleDocumentPageResponse.class);
        assertThat(eligibilitySchemas.get("SchemaDraftEvaluationEligibleDocumentPage").getProperties())
            .containsKeys("draftRevision", "currentAggregateId", "readiness", "blockingReason",
                "page", "size", "totalElements", "content");
    }

    @Test
    void exposesTypedGuidanceAndCandidateProperties() {
        Map<String, Schema> draftSchemas = ModelConverters.getInstance().readAll(SchemaDraftDtos.DraftResponse.class);
        Map<String, Schema> candidateSchemas = ModelConverters.getInstance().readAll(CandidatePageResponse.class);

        assertThat(draftSchemas).containsKeys("DraftResponse", "DraftGuidance", "DiscoveryGuidance");
        assertThat(draftSchemas.get("DraftResponse").getProperties()).containsKeys(
            "guidance", "guidanceRevision", "guidanceFingerprint", "currentAnalysis",
            "latestEvaluation", "latestReprocessing");
        assertThat(candidateSchemas).containsKey("CandidateResponse");
        assertThat(candidateSchemas.get("CandidateResponse").getProperties()).containsKeys(
            "recommendationState", "effectiveReviewState", "latestDecisionId", "evidence");
    }

    @Test
    void boundsGenericPageMetadataAndCopiesContent() {
        java.util.ArrayList<String> source = new java.util.ArrayList<>(List.of("one"));
        PageResponse<String> response = new PageResponse<>(-2, 500, -1, source);
        source.add("two");

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(100);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getContent()).containsExactly("one");
    }

    private void assertPageSchema(String expectedName, Class<?> type) {
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(type);
        assertThat(schemas).containsKey(expectedName);
        Schema page = schemas.get(expectedName);
        assertThat(page.getProperties()).containsKeys("page", "size", "totalElements", "content");
    }
}
