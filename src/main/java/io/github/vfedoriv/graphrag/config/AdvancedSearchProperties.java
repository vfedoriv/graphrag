package io.github.vfedoriv.graphrag.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.advanced-search")
public record AdvancedSearchProperties(
    @Min(1) int concurrency,
    @Min(1) int queueCapacity,
    @Min(1) int branchConcurrency,
    @NotBlank String fullTextAnalyzer
) { }
