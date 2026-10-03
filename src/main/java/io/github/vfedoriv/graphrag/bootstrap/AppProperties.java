package io.github.vfedoriv.graphrag.bootstrap;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotNull @Valid Neo4jProperties neo4j,
    @NotNull @Valid ModelProperties model,
    @NotNull @Valid StorageProperties storage,
    @NotNull @Valid ChunkingProperties chunking,
    @NotNull @Valid QueryProperties query,
    @NotNull @Valid ExtractionProperties extraction
) {

}
