package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BaseRequest(
    @Min(0) int page,
    @Min(1) @Max(500) int size
) {
}
