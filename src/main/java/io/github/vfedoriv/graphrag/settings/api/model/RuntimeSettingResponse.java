package io.github.vfedoriv.graphrag.settings.api.model;

import java.util.Map;

public record RuntimeSettingResponse(
    String key,
    String category,
    String valueType,
    Object currentValue,
    Object defaultValue,
    Object activeValue,
    String source,
    String lifecycleState,
    boolean mutable,
    boolean liveApplied,
    boolean sensitive,
    Map<String, Object> constraints,
    String updateMode,
    String reason,
    String label,
    String description,
    String effectiveChunkerRevision,
    String chunkMigrationLifecycle
) {
    public RuntimeSettingResponse(
        String key,
        String category,
        String valueType,
        Object currentValue,
        Object defaultValue,
        Object activeValue,
        String source,
        String lifecycleState,
        boolean mutable,
        boolean liveApplied,
        boolean sensitive,
        Map<String, Object> constraints,
        String updateMode,
        String reason,
        String label,
        String description
    ) {
        this(
            key, category, valueType, currentValue, defaultValue, activeValue, source, lifecycleState,
            mutable, liveApplied, sensitive, constraints, updateMode, reason, label, description,
            null, null
        );
    }
}
