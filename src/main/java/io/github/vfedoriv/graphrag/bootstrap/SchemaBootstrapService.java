package io.github.vfedoriv.graphrag.bootstrap;

import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SchemaBootstrapService {
    private final SchemaRegistryService schemaRegistryService;

    public SchemaBootstrapService(SchemaRegistryService schemaRegistryService) throws IOException {
        this.schemaRegistryService = schemaRegistryService;
        bootstrap();
    }

    public void bootstrap() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath:/schemas/*.json");
        log.info("Bootstrapping schemas: resourceCount={}", resources.length);
        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            try {
                schemaRegistryService.createSchema(content, SchemaSourceType.PREDEFINED);
                log.info("Bootstrapped schema resource: filename={}", resource.getFilename());
            } catch (ConflictException ignored) {
                log.debug("Bootstrapped schema already exists: filename={}", resource.getFilename());
            }
        }
    }
}
