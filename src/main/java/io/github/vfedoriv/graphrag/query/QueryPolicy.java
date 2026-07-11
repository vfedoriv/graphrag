package io.github.vfedoriv.graphrag.query;

import java.time.Duration;
import java.util.List;

/** Immutable query-safety settings captured at the start of a request. */
public record QueryPolicy(
    int maxRows,
    Duration timeout,
    boolean requireLimit,
    List<String> blockedKeywords
) {

    public QueryPolicy {
        blockedKeywords = List.copyOf(blockedKeywords);
    }

    public int timeoutSeconds() {
        return Math.toIntExact(timeout.toSeconds());
    }
}
