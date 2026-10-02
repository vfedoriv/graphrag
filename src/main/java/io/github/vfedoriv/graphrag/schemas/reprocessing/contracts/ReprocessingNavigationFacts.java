package io.github.vfedoriv.graphrag.schemas.reprocessing.contracts;

import java.util.List;
import java.util.Map;

public interface ReprocessingNavigationFacts {
    Map<String, Reference> latest(List<String> draftIds);
    record Reference(String id, String status, boolean current, String statusLocation) { }
}
