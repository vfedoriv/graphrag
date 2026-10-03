package io.github.vfedoriv.graphrag.schemas.reprocessing.configuration;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.schema-reprocessing")
public record SchemaReprocessingProperties(@Min(1) int concurrency, @Min(1) int queueCapacity) { }
