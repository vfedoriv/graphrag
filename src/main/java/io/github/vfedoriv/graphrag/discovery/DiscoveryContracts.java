package io.github.vfedoriv.graphrag.discovery;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class DiscoveryContracts {

    public static final String PROMPT_CONTRACT_REVISION = "schema-discovery-prompt-v1";
    public static final String CANDIDATE_CONTRACT_REVISION = "schema-discovery-candidates-v1";
    public static final String LIMITS_REVISION = "schema-discovery-limits-v1";

    private DiscoveryContracts() {
    }

    public enum SourceType { DOCUMENT, TEXT, FILE }
    public enum CandidateKind { NODE, NODE_PROPERTY, NODE_KEY, RELATIONSHIP, RELATIONSHIP_PROPERTY }
    public enum EvidenceOrigin { OBSERVED, GUIDED, INFERRED, EXISTING }
    public enum ReviewState { RECOMMENDED, LOW_SUPPORT, REVIEW_REQUIRED, SUPPRESSED }
    public enum ConflictCategory {
        PROPERTY_TYPE, IDENTITY_KEY, RELATIONSHIP_NAME, RELATIONSHIP_DIRECTION, PINNED_CONTEXT, ALIAS_SUGGESTION
    }
    public enum ResponseStatus { COMPLETED, PARTIAL }
    public enum SourceStatus { SUCCEEDED, FAILED }
    public enum FailureCategory {
        TIMEOUT(true), OVERLOADED(true), PROVIDER_ERROR(true), CONVERSION_ERROR(false), VALIDATION_ERROR(false);

        private final boolean retryable;

        FailureCategory(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    public record Evidence(
        String sourceId,
        String sourceFingerprint,
        String chunkId,
        String documentId,
        Set<EvidenceOrigin> origins
    ) {
        public Evidence {
            origins = orderedOrigins(origins);
        }
    }

    public record Candidate(
        CandidateKind kind,
        String identity,
        String label,
        String property,
        String propertyType,
        List<String> keys,
        String relationshipType,
        String fromLabel,
        String toLabel,
        String originalLabel,
        String originalProperty,
        String originalRelationshipType,
        Double confidence,
        Set<EvidenceOrigin> origins,
        List<Evidence> evidence,
        int supportCount,
        ReviewState reviewState
    ) {
        public Candidate {
            keys = keys == null ? List.of() : List.copyOf(keys);
            origins = orderedOrigins(origins);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record Conflict(
        ConflictCategory category,
        String coordinate,
        List<String> alternatives,
        List<Evidence> evidence,
        boolean reviewRequired
    ) {
    }

    public static String identity(
        CandidateKind kind, String label, String property, String relationshipType, String from, String to
    ) {
        return switch (kind) {
            case NODE -> "node:" + label;
            case NODE_PROPERTY -> "node-property:" + label + ":" + property;
            case NODE_KEY -> "node-key:" + label;
            case RELATIONSHIP -> "relationship:" + relationshipType + ":" + from + ":" + to;
            case RELATIONSHIP_PROPERTY -> "relationship-property:" + relationshipType + ":" + from + ":" + to + ":" + property;
        };
    }

    public static Set<EvidenceOrigin> orderedOrigins(Set<EvidenceOrigin> origins) {
        TreeSet<EvidenceOrigin> ordered = new TreeSet<>(Comparator.comparing(Enum::name));
        if (origins != null) {
            ordered.addAll(origins);
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    public static String normalizeLabel(String value) {
        String[] words = words(value);
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
    }

    public static String normalizeProperty(String value) {
        String label = normalizeLabel(value);
        return label.isEmpty() ? label : Character.toLowerCase(label.charAt(0)) + label.substring(1);
    }

    public static String normalizeRelationship(String value) {
        return String.join("_", words(value)).toUpperCase(Locale.ROOT);
    }

    private static String[] words(String value) {
        if (value == null) {
            return new String[0];
        }
        return value.trim().replaceAll("([a-z0-9])([A-Z])", "$1 $2").split("[^A-Za-z0-9]+");
    }
}
