package io.github.vfedoriv.graphrag.error;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;

public class AdvancedSearchResultUnavailableException extends ConflictException {
    private final AdvancedSearchRunStatus status;
    private final AdvancedSearchRunStage stage;

    public AdvancedSearchResultUnavailableException(AdvancedSearchRunStatus status, AdvancedSearchRunStage stage) {
        super("Advanced search result is not available");
        this.status = status;
        this.stage = stage;
    }
    public AdvancedSearchRunStatus getStatus() { return status; }
    public AdvancedSearchRunStage getStage() { return stage; }
}
