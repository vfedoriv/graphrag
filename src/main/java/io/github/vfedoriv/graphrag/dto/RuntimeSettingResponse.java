package io.github.vfedoriv.graphrag.dto;

import java.util.Map;

public record RuntimeSettingResponse(
    String key,
    String category,
    String valueType,
    Object currentValue,
    Object defaultValue,
    String source,
    boolean mutable,
    boolean liveApplied,
    boolean sensitive,
    Map<String, Object> constraints
) {
}
