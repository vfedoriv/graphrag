package io.github.vfedoriv.graphrag.documents.contracts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Revision-aware overwrite execution. Target variants encode the plan reason. */
public interface DocumentReprocessing {
    /** Check before target decoding; lookup failures must propagate to claim recovery. */
    boolean sourceMatches(Source source);

    /** Execute after a matching source check, without repeating the source lookup. */
    Result execute(Request request);

    record Source(String knowledgeBaseId, String documentId, String expectedSourceSha256) {
        public Source {
            Objects.requireNonNull(knowledgeBaseId);
            Objects.requireNonNull(documentId);
            Objects.requireNonNull(expectedSourceSha256);
        }
    }

    record Request(String knowledgeBaseId, String documentId, String expectedSourceSha256,
                   String profileScopeId, Target target) {
        public Request {
            Objects.requireNonNull(knowledgeBaseId);
            Objects.requireNonNull(documentId);
            Objects.requireNonNull(expectedSourceSha256);
            Objects.requireNonNull(target);
        }
    }

    sealed interface Target permits Activation, Migration { }

    record Activation(Map<String, Object> processingOptions) implements Target {
        public Activation { processingOptions = immutableOptions(processingOptions); }
    }

    record Migration(String aiProfileId, long aiProfileRevision, String embeddingSpaceId,
                     String schemaId, String schemaContentHash, ChunkTarget chunkTarget,
                     DocumentTarget documentTarget) implements Target {
        public Migration {
            Objects.requireNonNull(aiProfileId);
            Objects.requireNonNull(embeddingSpaceId);
            Objects.requireNonNull(schemaId);
            Objects.requireNonNull(schemaContentHash);
            Objects.requireNonNull(chunkTarget);
            Objects.requireNonNull(documentTarget);
        }
    }

    record ChunkTarget(String strategyName, String strategyRevision, int targetTokens,
                       int overlapTokens, int hardCharacterLimit, int parentTargetTokens,
                       int parentHardCharacterLimit, int parentMaxPages, int contextHeaderMaxTokens,
                       int contextHeaderMaxCharacters, String tokenizerId, String tokenizerRevision,
                       String tokenCountMode, String representationRevision, String settingsHash) {
        public ChunkTarget {
            Objects.requireNonNull(strategyName);
            Objects.requireNonNull(strategyRevision);
            Objects.requireNonNull(tokenizerId);
            Objects.requireNonNull(tokenizerRevision);
            Objects.requireNonNull(tokenCountMode);
            Objects.requireNonNull(representationRevision);
            Objects.requireNonNull(settingsHash);
        }
    }

    record DocumentTarget(String sourceSha256, String parserId, String parserRevision,
                          String fileFormat, String effectiveChunkerRevision,
                          Map<String, Object> effectiveProcessingOptions) {
        public DocumentTarget {
            Objects.requireNonNull(sourceSha256);
            Objects.requireNonNull(parserId);
            Objects.requireNonNull(parserRevision);
            Objects.requireNonNull(fileFormat);
            Objects.requireNonNull(effectiveChunkerRevision);
            effectiveProcessingOptions = immutableOptions(effectiveProcessingOptions);
        }
    }

    enum Status { STALE_SOURCE, SUCCEEDED, FAILED }

    /** Failure category contains only a stable code or originating exception class, never content. */
    record Result(Status status, String failureCategory) {
        public Result { Objects.requireNonNull(status); }
    }

    private static Map<String, Object> immutableOptions(Map<String, Object> options) {
        Objects.requireNonNull(options);
        Map<String, Object> copy = new LinkedHashMap<>();
        options.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(key, immutableValue(entry)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(DocumentReprocessing::immutableValue).toList();
        }
        return value;
    }
}
