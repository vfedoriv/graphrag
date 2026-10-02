package io.github.vfedoriv.graphrag.schemas.reprocessing.domain;

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
