package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.error.ConflictException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class SchemaBootstrapService {

    public SchemaBootstrapService(SchemaRegistryService schemaRegistryService) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath:/schemas/*.yaml");
        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            try {
                schemaRegistryService.createSchema(content, SchemaSourceType.PREDEFINED);
            } catch (ConflictException ignored) {
                // Bootstrapped schema already exists.
            }
        }
    }
}
