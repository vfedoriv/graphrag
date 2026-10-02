package io.github.vfedoriv.graphrag.search.runs.domain;

public enum AdvancedSearchRunStatus {
    QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED, CANCELLED, INTERRUPTED;

    public boolean terminal() {
        return this == COMPLETED || this == PARTIAL || this == FAILED
            || this == CANCELLED || this == INTERRUPTED;
    }
}
