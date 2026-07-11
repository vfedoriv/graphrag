package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.query.QueryPolicy;
import java.util.List;

public record QueryPolicyResponse(
    int maxRows,
    int timeoutSeconds,
    boolean requireLimit,
    List<String> blockedKeywords
) {

    public QueryPolicyResponse {
        blockedKeywords = List.copyOf(blockedKeywords);
    }

    public static QueryPolicyResponse from(QueryPolicy policy) {
        return new QueryPolicyResponse(
            policy.maxRows(),
            policy.timeoutSeconds(),
            policy.requireLimit(),
            policy.blockedKeywords()
        );
    }
}
