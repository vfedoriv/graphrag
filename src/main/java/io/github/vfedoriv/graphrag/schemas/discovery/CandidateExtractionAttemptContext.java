package io.github.vfedoriv.graphrag.schemas.discovery;

import io.github.vfedoriv.graphrag.service.AiProfileContext;
import java.util.LinkedHashMap;
import java.util.Map;

public record CandidateExtractionAttemptContext(
    String draftId,
    String runId,
    String sourceId,
    Long sourceRevision,
    String chunkId,
    String profileId,
    Long profileRevision,
    Integer outputAttempt,
    Integer configuredTimeoutSeconds,
    Integer configuredSdkMaxRetries,
    Long sourceDeadlineNanos,
    Long requestDeadlineNanos
) {
    public static CandidateExtractionAttemptContext forSource(String sourceId) {
        return new CandidateExtractionAttemptContext(
            null, null, sourceId, null, null, AiProfileContext.activeProfileId(), null, null, null, null, null, null);
    }

    public CandidateExtractionAttemptContext forChunk(String nextChunkId) {
        return new CandidateExtractionAttemptContext(draftId, runId, sourceId, sourceRevision, nextChunkId,
            profileId, profileRevision, outputAttempt, configuredTimeoutSeconds, configuredSdkMaxRetries,
            sourceDeadlineNanos, requestDeadlineNanos);
    }

    public CandidateExtractionAttemptContext forOutputAttempt(int nextOutputAttempt) {
        return new CandidateExtractionAttemptContext(draftId, runId, sourceId, sourceRevision, chunkId,
            profileId, profileRevision, nextOutputAttempt, configuredTimeoutSeconds, configuredSdkMaxRetries,
            sourceDeadlineNanos, requestDeadlineNanos);
    }

    public CandidateExtractionAttemptContext withDeadlines(long sourceDeadline, long requestDeadline) {
        return new CandidateExtractionAttemptContext(draftId, runId, sourceId, sourceRevision, chunkId,
            profileId, profileRevision, outputAttempt, configuredTimeoutSeconds, configuredSdkMaxRetries,
            sourceDeadline, requestDeadline);
    }

    public void requireRemainingBudget() {
        long now = System.nanoTime();
        if (requestDeadlineNanos != null && now >= requestDeadlineNanos) {
            throw new DiscoveryDeadlineExceededException(SourceFailureCode.REQUEST_DEADLINE_EXCEEDED);
        }
        if (sourceDeadlineNanos != null && now >= sourceDeadlineNanos) {
            throw new DiscoveryDeadlineExceededException(SourceFailureCode.SOURCE_DEADLINE_EXCEEDED);
        }
    }

    public Map<String, String> observationAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, "ai.draft.id", draftId);
        put(attributes, "ai.draft.run_id", runId);
        put(attributes, "ai.discovery.source_id", sourceId);
        put(attributes, "ai.discovery.source_revision", sourceRevision);
        put(attributes, "ai.discovery.chunk_id", chunkId);
        put(attributes, "ai.profile.id", profileId);
        put(attributes, "ai.profile.revision", profileRevision);
        put(attributes, "ai.output_attempt", outputAttempt);
        put(attributes, "ai.profile.timeout_seconds", configuredTimeoutSeconds);
        put(attributes, "ai.profile.sdk_max_retries", configuredSdkMaxRetries);
        return Map.copyOf(attributes);
    }

    private static void put(Map<String, String> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, String.valueOf(value));
        }
    }
}
