package io.github.vfedoriv.graphrag.search.retrieval.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class AdvancedSearchTextRetrievalContracts {

    public static final int MAX_SUBQUERIES = 8;
    public static final int MAX_CANDIDATES = 200;

    private AdvancedSearchTextRetrievalContracts() { }

    public enum Branch {
        DENSE,
        LEXICAL,
        METADATA
    }

    public enum Status {
        COMPLETED,
        FAILED,
        DEADLINE_EXCEEDED
    }

    public record Subquery(String id, String text) {
        public Subquery {
            requireNonBlank(id, "subquery id");
            requireNonBlank(text, "subquery text");
        }
    }

    public record MetadataConstraints(String filename, String contentType) {
        public boolean present() {
            return hasText(filename) || hasText(contentType);
        }
    }

    public record Request(
        String knowledgeBaseId,
        List<Subquery> subqueries,
        MetadataConstraints metadata,
        int candidateLimit,
        boolean includeText,
        Instant deadline
    ) {
        public Request {
            requireNonBlank(knowledgeBaseId, "knowledgeBaseId");
            subqueries = subqueries == null ? List.of() : List.copyOf(subqueries);
            if (subqueries.size() > MAX_SUBQUERIES) {
                throw new IllegalArgumentException("subqueries must contain at most " + MAX_SUBQUERIES + " entries");
            }
            if (candidateLimit < 1 || candidateLimit > MAX_CANDIDATES) {
                throw new IllegalArgumentException("candidateLimit must be between 1 and " + MAX_CANDIDATES);
            }
            if (deadline == null) {
                throw new IllegalArgumentException("deadline must not be null");
            }
            metadata = metadata == null ? new MetadataConstraints(null, null) : metadata;
        }

        public boolean expired() {
            return !Instant.now().isBefore(deadline);
        }

        public Duration remaining() {
            Duration remaining = Duration.between(Instant.now(), deadline);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }
    }

    public record SourceIdentity(
        String chunkId,
        String documentId,
        int chunkIndex,
        Integer sourceStart,
        Integer sourceEnd,
        Integer pageStart,
        Integer pageEnd,
        String processingRunId,
        String effectiveChunkerRevision,
        String structuralPath
    ) { }

    public record Candidate(
        Branch branch,
        SourceIdentity source,
        String subqueryId,
        int rank,
        double rawScore,
        String text
    ) {
        public Candidate {
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be positive");
            }
        }
    }

    public record Diagnostics(
        Status status,
        long latencyMs,
        int candidateCount,
        String failureCategory
    ) {
        public Diagnostics {
            if (latencyMs < 0 || candidateCount < 0) {
                throw new IllegalArgumentException("diagnostic counts must not be negative");
            }
            failureCategory = sanitizeCategory(failureCategory);
        }
    }

    public record Result(Branch branch, List<Candidate> candidates, Diagnostics diagnostics) {
        public Result {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            if (candidates.size() > MAX_CANDIDATES * MAX_SUBQUERIES) {
                throw new IllegalArgumentException("branch result exceeds the bounded candidate envelope");
            }
        }
    }

    private static String sanitizeCategory(String value) {
        if (!hasText(value)) {
            return null;
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(80, sanitized.length()));
    }

    private static void requireNonBlank(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
