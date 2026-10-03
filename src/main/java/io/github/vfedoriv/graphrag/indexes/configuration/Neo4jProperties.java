package io.github.vfedoriv.graphrag.indexes.configuration;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.neo4j")
public record Neo4jProperties(
    @NotBlank String database
) {
}
