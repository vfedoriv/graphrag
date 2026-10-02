package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import java.util.List;
import java.util.Map;

public interface DraftReprocessingSummaries {
    Map<String, Reference> latest(List<String> draftIds);
    record Reference(String id, String status, boolean current, String statusLocation) { }
}
