package io.github.vfedoriv.graphrag.domain;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import java.util.List;

public final class AdvancedSearchRankingContracts {

    public static final int RRF_K = 60;
    public static final int MAX_FUSION_POOL = 60;
    public static final int MAX_EXPANSION_SEEDS = 10;
    public static final int MAX_GRAPH_DERIVED_CANDIDATES = 20;
    public static final int MAX_RERANK_POOL = 20;
    public static final int DEFAULT_FINAL_EVIDENCE = 10;
    public static final int MAX_FINAL_EVIDENCE = 20;
    public static final int DEFAULT_PER_DOCUMENT_CAP = 3;

    private AdvancedSearchRankingContracts() { }

    public enum Channel {
        DENSE,
        LEXICAL,
        METADATA,
        GRAPH
    }

    public enum CitationKind {
        TEXT_CHILD,
        GRAPH_PARENT
    }

    public record SourceBounds(
        Integer sourceStart,
        Integer sourceEnd,
        Integer pageStart,
        Integer pageEnd
    ) { }

    public record EvidenceSource(
        String chunkId,
        String documentId,
        int chunkIndex,
        SourceBounds bounds,
        String processingRunId,
        String effectiveChunkerRevision,
        String structuralPath
    ) {
        public EvidenceSource {
            requireNonBlank(chunkId, "chunkId");
            requireNonBlank(documentId, "documentId");
            bounds = bounds == null ? new SourceBounds(null, null, null, null) : bounds;
        }
    }

    public record ChannelContribution(
        Channel channel,
        String subqueryId,
        int rawRank,
        double rawScore,
        SourceBounds sourceBounds
    ) {
        public ChannelContribution {
            if (channel == null) {
                throw new IllegalArgumentException("channel must not be null");
            }
            if (rawRank < 1) {
                throw new IllegalArgumentException("rawRank must be positive");
            }
            sourceBounds = sourceBounds == null ? new SourceBounds(null, null, null, null) : sourceBounds;
        }
    }

    public record ParentContext(
        String contextChunkId,
        String documentId,
        String kind,
        String text,
        SourceBounds bounds,
        String effectiveChunkerRevision,
        List<String> contributingChunkIds,
        int tokenEstimate
    ) {
        public ParentContext {
            contributingChunkIds = contributingChunkIds == null ? List.of() : List.copyOf(contributingChunkIds);
            bounds = bounds == null ? new SourceBounds(null, null, null, null) : bounds;
        }
    }

    public record EvidenceCandidate(
        EvidenceSource source,
        CitationKind citationKind,
        String text,
        List<ChannelContribution> contributions,
        List<GraphFact> graphFacts,
        ParentContext parentContext,
        double fusedScore,
        int fusedRank,
        Double rerankScore
    ) {
        public EvidenceCandidate {
            if (source == null || citationKind == null) {
                throw new IllegalArgumentException("source and citationKind must not be null");
            }
            contributions = contributions == null ? List.of() : List.copyOf(contributions);
            graphFacts = graphFacts == null ? List.of() : List.copyOf(graphFacts);
            if (fusedRank < 0) {
                throw new IllegalArgumentException("fusedRank must not be negative");
            }
        }

        public String chunkId() {
            return source.chunkId();
        }

        public String documentId() {
            return source.documentId();
        }

        public EvidenceCandidate withFusion(double score, int rank) {
            return new EvidenceCandidate(
                source, citationKind, text, contributions, graphFacts, parentContext, score, rank, rerankScore
            );
        }

        public EvidenceCandidate withGraphFacts(List<GraphFact> facts) {
            return new EvidenceCandidate(
                source, citationKind, text, contributions, facts, parentContext, fusedScore, fusedRank, rerankScore
            );
        }

        public EvidenceCandidate withParentContext(ParentContext context) {
            return new EvidenceCandidate(
                source, citationKind, text, contributions, graphFacts, context, fusedScore, fusedRank, rerankScore
            );
        }

        public EvidenceCandidate withRerankScore(Double score) {
            return new EvidenceCandidate(
                source, citationKind, text, contributions, graphFacts, parentContext, fusedScore, fusedRank, score
            );
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
