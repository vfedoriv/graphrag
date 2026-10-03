package io.github.vfedoriv.graphrag.storage.configuration;

import jakarta.validation.constraints.*;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    @NotNull Path documentsRoot
) {
}
