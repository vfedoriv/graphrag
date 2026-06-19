package io.github.vfedoriv.graphrag.dto;

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
    String description
) {
}
