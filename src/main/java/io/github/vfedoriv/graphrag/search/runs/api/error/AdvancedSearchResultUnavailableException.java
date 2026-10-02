package io.github.vfedoriv.graphrag.search.runs.api.error;

import io.github.vfedoriv.graphrag.error.ConflictException;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStatus;

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
