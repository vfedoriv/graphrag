package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.AdvisoryAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.CoordinateAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Metrics;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.QuestionAssessment;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Rate;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AdvisoryAssessmentResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AdvisoryExecutionStatus;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AdvisoryReasonResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.CoordinateAssessmentResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.CountMetricResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationMetricsResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationReasonResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationReproducibilityResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.MetricApplicability;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.MetricEvidenceResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.MetricIdentifier;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.QuestionAssessmentResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.QuestionCoverage;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.RateMetricResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SchemaDraftEvaluationContractMapper {
    private final SchemaDraftJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    public SchemaDraftEvaluationContractMapper(SchemaDraftJsonSupport jsonSupport, ObjectMapper objectMapper) {
        this.jsonSupport = jsonSupport;
        this.objectMapper = objectMapper;
    }

    public EvaluationMetricsResponse metrics(String json, List<String> evidenceCoordinates) {
        if (json == null) {
            return null;
        }
        JsonNode root = jsonSupport.parse(json);
        if (root.has("rates") && root.has("counts")) {
            return objectMapper.convertValue(root, EvaluationMetricsResponse.class);
        }
        return metrics(objectMapper.convertValue(root, Metrics.class), evidenceCoordinates);
    }

    public EvaluationMetricsResponse metrics(Metrics value, List<String> evidenceCoordinates) {
        List<MetricEvidenceResponse> evidence = evidenceCoordinates == null ? List.of()
            : evidenceCoordinates.stream().map(MetricEvidenceResponse::new).toList();
        List<RateMetricResponse> rates = List.of(
            rate(MetricIdentifier.RECOGNIZED_ENTITY_RATE, value.recognizedEntityRate(), evidence),
            rate(MetricIdentifier.DROPPED_RELATIONSHIP_RATE, value.droppedRelationshipRate(), evidence),
            rate(MetricIdentifier.KEY_AVAILABILITY_RATE, value.keyAvailabilityRate(), evidence));
        List<CountMetricResponse> counts = List.of(
            count(MetricIdentifier.RECOGNIZED_ENTITIES, value.recognizedEntities(), evidence),
            count(MetricIdentifier.UNKNOWN_ENTITIES, value.unknownEntities(), evidence),
            count(MetricIdentifier.RELATIONSHIPS, value.relationships(), evidence),
            count(MetricIdentifier.DROPPED_RELATIONSHIPS, value.droppedRelationships(), evidence),
            count(MetricIdentifier.NODES_REQUIRING_KEYS, value.nodesRequiringKeys(), evidence),
            count(MetricIdentifier.NODES_WITH_KEYS, value.nodesWithKeys(), evidence),
            count(MetricIdentifier.PROPERTY_TYPE_CONFLICTS, value.propertyTypeConflicts(), evidence),
            count(MetricIdentifier.MISSING_REQUIRED_PROPERTIES, value.missingRequiredProperties(), evidence),
            count(MetricIdentifier.LOW_SUPPORT_CANDIDATES, value.lowSupportCandidates(), evidence),
            count(MetricIdentifier.GUIDED_WITHOUT_EVIDENCE_CANDIDATES, value.guidedWithoutEvidenceCandidates(), evidence));
        List<EvaluationReasonResponse> reasons = value.droppedRelationshipReasons().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new EvaluationReasonResponse(entry.getKey(), Long.toString(entry.getValue())))
            .toList();
        return new EvaluationMetricsResponse(rates, counts, reasons);
    }

    public Metrics domainMetrics(String json) {
        JsonNode root = jsonSupport.parse(json);
        if (!root.has("rates")) {
            return objectMapper.convertValue(root, Metrics.class);
        }
        EvaluationMetricsResponse response = objectMapper.convertValue(root, EvaluationMetricsResponse.class);
        Map<MetricIdentifier, RateMetricResponse> rates = response.rates().stream()
            .collect(Collectors.toMap(RateMetricResponse::metric, Function.identity()));
        Map<MetricIdentifier, CountMetricResponse> counts = response.counts().stream()
            .collect(Collectors.toMap(CountMetricResponse::metric, Function.identity()));
        Map<String, Long> reasons = response.reasons().stream().collect(Collectors.toMap(
            EvaluationReasonResponse::code, value -> parseLong(value.detail())));
        return new Metrics(
            count(counts, MetricIdentifier.RECOGNIZED_ENTITIES), count(counts, MetricIdentifier.UNKNOWN_ENTITIES),
            rate(rates, MetricIdentifier.RECOGNIZED_ENTITY_RATE), count(counts, MetricIdentifier.RELATIONSHIPS),
            count(counts, MetricIdentifier.DROPPED_RELATIONSHIPS), rate(rates, MetricIdentifier.DROPPED_RELATIONSHIP_RATE),
            count(counts, MetricIdentifier.NODES_REQUIRING_KEYS), count(counts, MetricIdentifier.NODES_WITH_KEYS),
            rate(rates, MetricIdentifier.KEY_AVAILABILITY_RATE), count(counts, MetricIdentifier.PROPERTY_TYPE_CONFLICTS),
            count(counts, MetricIdentifier.MISSING_REQUIRED_PROPERTIES), count(counts, MetricIdentifier.LOW_SUPPORT_CANDIDATES),
            count(counts, MetricIdentifier.GUIDED_WITHOUT_EVIDENCE_CANDIDATES), reasons);
    }

    public AdvisoryAssessmentResponse advisory(String json, String contractRevision) {
        if (json == null) {
            return null;
        }
        JsonNode root = jsonSupport.parse(json);
        if (root.has("reproducibility")) {
            return objectMapper.convertValue(root, AdvisoryAssessmentResponse.class);
        }
        AdvisoryAssessment legacy = objectMapper.convertValue(root, AdvisoryAssessment.class);
        return advisory(legacy, contractRevision);
    }

    public AdvisoryAssessmentResponse advisory(AdvisoryAssessment value, String contractRevision) {
        List<QuestionAssessmentResponse> questions = value.intendedQuestions().stream()
            .map(this::question).toList();
        List<CoordinateAssessmentResponse> noise = value.schemaNoise().stream()
            .map(this::coordinate).toList();
        List<String> warnings = value.warning() == null ? List.of() : List.of(value.warning());
        return new AdvisoryAssessmentResponse(status(value.status()), questions, noise, List.of(), warnings,
            new EvaluationReproducibilityResponse(value.profileId(), value.profileRevision(),
                value.promptRevision(), contractRevision));
    }

    private RateMetricResponse rate(MetricIdentifier id, Rate value, List<MetricEvidenceResponse> evidence) {
        MetricApplicability applicability = value.applicable()
            ? MetricApplicability.APPLICABLE : MetricApplicability.NOT_APPLICABLE;
        return new RateMetricResponse(id, value.numerator(), value.denominator(), value.value(), applicability, evidence);
    }

    private CountMetricResponse count(MetricIdentifier id, long value, List<MetricEvidenceResponse> evidence) {
        return new CountMetricResponse(id, value, evidence);
    }

    private long count(Map<MetricIdentifier, CountMetricResponse> values, MetricIdentifier id) {
        CountMetricResponse value = values.get(id);
        return value == null ? 0 : value.count();
    }

    private Rate rate(Map<MetricIdentifier, RateMetricResponse> values, MetricIdentifier id) {
        RateMetricResponse value = values.get(id);
        return value == null ? Rate.of(0, 0)
            : new Rate(value.numerator(), value.denominator(), value.value(),
                value.applicability() == MetricApplicability.APPLICABLE);
    }

    private QuestionAssessmentResponse question(QuestionAssessment value) {
        return new QuestionAssessmentResponse(value.questionFingerprint(), coverage(value.assessment()),
            List.of(), value.schemaCoordinates() == null ? List.of() : List.copyOf(value.schemaCoordinates()));
    }

    private CoordinateAssessmentResponse coordinate(CoordinateAssessment value) {
        List<AdvisoryReasonResponse> reasons = value.reason() == null ? List.of()
            : List.of(new AdvisoryReasonResponse("LEGACY_REASON", value.reason()));
        return new CoordinateAssessmentResponse(value.schemaCoordinate(), value.assessment(), reasons);
    }

    private AdvisoryExecutionStatus status(String value) {
        try {
            return AdvisoryExecutionStatus.valueOf(value);
        } catch (RuntimeException exception) {
            return AdvisoryExecutionStatus.FAILED;
        }
    }

    private QuestionCoverage coverage(String value) {
        try {
            return QuestionCoverage.valueOf(value);
        } catch (RuntimeException exception) {
            return QuestionCoverage.UNASSESSED;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException exception) {
            return 0;
        }
    }
}
