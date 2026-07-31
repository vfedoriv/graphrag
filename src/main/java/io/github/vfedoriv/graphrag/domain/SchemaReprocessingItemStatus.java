package io.github.vfedoriv.graphrag.domain;

public enum SchemaReprocessingItemStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    STALE_SOURCE,
    BLOCKED_TARGET_CHANGED,
    BLOCKED,
    INTERRUPTED,
    SKIPPED
}
