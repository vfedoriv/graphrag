package io.github.vfedoriv.graphrag.schemas.evaluation.domain;

import java.util.List;
import java.util.Map;

public final class SchemaDraftEvaluationContracts {
    public static final String CONTRACT_REVISION = "schema-draft-evaluation-v2";
    public static final String PROMPT_REVISION = "schema-draft-advisory-v1";

    private SchemaDraftEvaluationContracts() { }

    public record Rate(long numerator, long denominator, Double value, boolean applicable) {
        public static Rate of(long numerator, long denominator) {
            return denominator == 0 ? new Rate(numerator, denominator, null, false)
                : new Rate(numerator, denominator, (double) numerator / denominator, true);
        }
    }

    public record Metrics(
        long recognizedEntities,
        long unknownEntities,
        Rate recognizedEntityRate,
        long relationships,
        long droppedRelationships,
        Rate droppedRelationshipRate,
        long nodesRequiringKeys,
        long nodesWithKeys,
        Rate keyAvailabilityRate,
        long propertyTypeConflicts,
        long missingRequiredProperties,
        long lowSupportCandidates,
        long guidedWithoutEvidenceCandidates,
        Map<String, Long> droppedRelationshipReasons
    ) { }

    public record AdvisoryAssessment(
        String status,
        List<QuestionAssessment> intendedQuestions,
        List<CoordinateAssessment> schemaNoise,
        String profileId,
        long profileRevision,
        String promptRevision,
        String warning
    ) { }

    public record QuestionAssessment(String questionFingerprint, String assessment, List<String> schemaCoordinates) { }
    public record CoordinateAssessment(String schemaCoordinate, String assessment, String reason) { }
}
