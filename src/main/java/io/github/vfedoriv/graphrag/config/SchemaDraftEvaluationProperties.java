package io.github.vfedoriv.graphrag.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.schema-draft.evaluation")
public record SchemaDraftEvaluationProperties(
    @Min(1) int concurrency,
    @Min(1) int queueCapacity,
    @Min(0) int minimumSuccessfulDocuments,
    boolean requiredForPublication,
    boolean advisoryEnabledByDefault
) { }
