package io.github.vfedoriv.graphrag.search.query.domain;

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

    public static QueryPolicy from(io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess.QuerySettings settings) {
        return new QueryPolicy(settings.maxRows(), Duration.ofSeconds(settings.timeoutSeconds()),
            settings.requireLimit(), settings.blockedKeywords());
    }

    public int timeoutSeconds() {
        return Math.toIntExact(timeout.toSeconds());
    }
}
