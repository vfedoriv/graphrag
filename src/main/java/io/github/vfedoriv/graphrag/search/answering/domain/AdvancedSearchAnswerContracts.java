package io.github.vfedoriv.graphrag.search.answering.domain;

import java.util.List;

public final class AdvancedSearchAnswerContracts {

    public static final int ANSWER_VERSION = 1;
    public static final int MAX_CLAIMS = 40;
    public static final int MAX_LIMITATIONS = 20;

    private AdvancedSearchAnswerContracts() { }

    public enum AnswerStatus {
        ANSWERED,
        INSUFFICIENT_EVIDENCE,
        ANSWER_UNAVAILABLE
    }

    public enum ConfidenceLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum ClaimKind {
        TEXT,
        GRAPH
    }

    public enum CitationType {
        TEXT_CHILD,
        GRAPH_PARENT,
        CONTEXT_ONLY
    }

    public record Confidence(ConfidenceLevel level, double score) { }

    public record Limitation(String code, String description) { }

    public record Claim(
        String id,
        ClaimKind kind,
        String text,
        List<String> citationIds,
        List<String> graphFactIds,
        List<String> graphEvidenceIds
    ) {
        public Claim {
            citationIds = citationIds == null ? List.of() : List.copyOf(citationIds);
            graphFactIds = graphFactIds == null ? List.of() : List.copyOf(graphFactIds);
            graphEvidenceIds = graphEvidenceIds == null ? List.of() : List.copyOf(graphEvidenceIds);
        }
    }

    public record Answer(
        int version,
        AnswerStatus status,
        String text,
        Confidence confidence,
        List<Limitation> limitations,
        List<Claim> claims
    ) {
        public Answer {
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            claims = claims == null ? List.of() : List.copyOf(claims);
        }
    }

    public record SourceRange(
        Integer sourceStart,
        Integer sourceEnd,
        Integer pageStart,
        Integer pageEnd
    ) { }

    public record Evidence(
        String citationId,
        CitationType type,
        String chunkId,
        String documentId,
        SourceRange range,
        String processingRunId,
        String effectiveChunkerRevision,
        String structuralPath,
        String text,
        int rank,
        Double score,
        String sourceFilename,
        String sourceContentType,
        String sourceDisplayLabel
    ) {
        public Evidence(
            String citationId,
            CitationType type,
            String chunkId,
            String documentId,
            SourceRange range,
            String processingRunId,
            String effectiveChunkerRevision,
            String structuralPath,
            String text
        ) {
            this(citationId, type, chunkId, documentId, range, processingRunId,
                effectiveChunkerRevision, structuralPath, text, 0, null, null, null, null);
        }

        public Evidence(
            String citationId,
            CitationType type,
            String chunkId,
            String documentId,
            SourceRange range,
            String processingRunId,
            String effectiveChunkerRevision,
            String structuralPath,
            String text,
            int rank,
            Double score
        ) {
            this(citationId, type, chunkId, documentId, range, processingRunId,
                effectiveChunkerRevision, structuralPath, text, rank, score, null, null, null);
        }
    }

    public record GraphFact(
        String factId,
        List<String> evidenceIds,
        List<String> citationIds
    ) {
        public GraphFact {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
            citationIds = citationIds == null ? List.of() : List.copyOf(citationIds);
        }
    }

    public record DiagnosticResult(
        boolean repairAttempted,
        boolean repairSucceeded,
        boolean abstained,
        int citationCount,
        int claimCount,
        String outcomeCategory
    ) { }
}
