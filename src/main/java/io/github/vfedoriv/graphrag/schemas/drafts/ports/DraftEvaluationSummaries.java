package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import java.util.List;
import java.util.Map;

public interface DraftEvaluationSummaries {
    Map<String, Reference> latest(List<Context> drafts);
    record Context(String draftId, long revision, String aggregateRevisionId) { }
    record Reference(String id, String status, boolean current, String statusLocation) { }
}
