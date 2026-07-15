package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.AdvisoryAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.CoordinateAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Metrics;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.QuestionAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Rate;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AdvisoryAssessmentResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DraftGuidance;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationMetricsResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.MetricApplicability;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.MetricIdentifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaDraftApiContractMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SchemaDraftJsonSupport jsonSupport = new SchemaDraftJsonSupport(objectMapper);
    private final SchemaDraftGuidanceMapper guidanceMapper = new SchemaDraftGuidanceMapper(jsonSupport, objectMapper);
    private final SchemaDraftEvaluationContractMapper evaluationMapper =
        new SchemaDraftEvaluationContractMapper(jsonSupport, objectMapper);

    @Test
    void readsEveryLegacyGuidanceShapeAndWritesCanonicalEnvelope() {
        DraftGuidance direct = guidanceMapper.read("{\"domainDescription\":\"direct\",\"intendedQuestions\":[\"q\"]}");
        DraftGuidance instructions = guidanceMapper.read("{\"additionalInstructions\":\"legacy instructions\"}");
        DraftGuidance wrapped = guidanceMapper.read("""
            {"additionalInstructions":"wrapped instructions","guidance":{"domainDescription":"wrapped"}}
            """);

        assertThat(direct.guidance().domainDescription()).isEqualTo("direct");
        assertThat(direct.guidance().intendedQuestions()).containsExactly("q");
        assertThat(instructions.additionalInstructions()).isEqualTo("legacy instructions");
        assertThat(instructions.guidance().requiredConcepts()).isEmpty();
        assertThat(wrapped.additionalInstructions()).isEqualTo("wrapped instructions");
        assertThat(wrapped.guidance().domainDescription()).isEqualTo("wrapped");

        String canonical = guidanceMapper.canonical(direct);
        assertThat(canonical).contains("\"additionalInstructions\"", "\"guidance\"", "\"domainDescription\":\"direct\"");
    }

    @Test
    void rejectsUnknownApiAndPersistedGuidanceFields() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"unknown\":true}", DraftGuidance.class))
            .hasMessageContaining("Unknown draft guidance field");
        assertThatThrownBy(() -> objectMapper.readValue(
            "{\"guidance\":{\"requiredConcepts\":[{\"name\":\"Person\",\"unknown\":true}]}}",
            DraftGuidance.class)).hasMessageContaining("Unknown required concept field");
        assertThatThrownBy(() -> guidanceMapper.read("{\"unknown\":true}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Persisted draft guidance has an unsupported shape");

        tools.jackson.databind.json.JsonMapper mvcMapper = new tools.jackson.databind.json.JsonMapper();
        assertThatThrownBy(() -> mvcMapper.readValue("{\"unknown\":true}", DraftGuidance.class))
            .hasMessageContaining("Unknown draft guidance field");
        assertThat(mvcMapper.readValue(
            "{\"additionalInstructions\":\"valid\",\"guidance\":{\"domainDescription\":\"domain\"}}",
            DraftGuidance.class).guidance().domainDescription()).isEqualTo("domain");
    }

    @Test
    void adaptsVersionOneMetricsAndAdvisoryWithDeterministicDefaults() {
        Metrics legacyMetrics = new Metrics(3, 1, Rate.of(3, 4), 2, 0, Rate.of(0, 2),
            0, 0, Rate.of(0, 0), 1, 2, 3, 4, Map.of("UNKNOWN_ENDPOINT", 2L));
        EvaluationMetricsResponse metrics = evaluationMapper.metrics(jsonSupport.canonical(legacyMetrics), List.of("node:Person"));

        assertThat(metrics.rates()).filteredOn(value -> value.metric() == MetricIdentifier.KEY_AVAILABILITY_RATE)
            .singleElement().satisfies(value -> {
                assertThat(value.value()).isNull();
                assertThat(value.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
            });
        assertThat(metrics.counts()).filteredOn(value -> value.metric() == MetricIdentifier.PROPERTY_TYPE_CONFLICTS)
            .singleElement().extracting(value -> value.count()).isEqualTo(1L);
        assertThat(metrics.rates()).allSatisfy(value -> assertThat(value.evidence()).hasSize(1));

        AdvisoryAssessment legacyAdvisory = new AdvisoryAssessment("COMPLETED_WITHOUT_MODEL_JUDGMENT",
            List.of(new QuestionAssessment("fingerprint", "UNASSESSED", List.of())),
            List.of(new CoordinateAssessment("node:Person", "POSSIBLE_NOISE", "legacy reason")),
            "profile", 7, "prompt-v1", "legacy warning");
        AdvisoryAssessmentResponse advisory = evaluationMapper.advisory(
            jsonSupport.canonical(legacyAdvisory), "schema-draft-evaluation-v1");

        assertThat(advisory.reasons()).isEmpty();
        assertThat(advisory.intendedQuestions()).singleElement().satisfies(value -> {
            assertThat(value.reasons()).isEmpty();
            assertThat(value.schemaCoordinates()).isEmpty();
        });
        assertThat(advisory.warnings()).containsExactly("legacy warning");
        assertThat(advisory.reproducibility().contractRevision()).isEqualTo("schema-draft-evaluation-v1");
    }

    @Test
    void versionTwoMetricJsonRoundTripsToTheInternalCombinationModel() {
        Metrics source = new Metrics(2, 0, Rate.of(2, 2), 1, 0, Rate.of(0, 1),
            1, 1, Rate.of(1, 1), 0, 0, 0, 0, Map.of());
        String versionTwoJson = jsonSupport.canonical(evaluationMapper.metrics(source, List.of()));

        assertThat(evaluationMapper.domainMetrics(versionTwoJson)).isEqualTo(source);
    }
}
