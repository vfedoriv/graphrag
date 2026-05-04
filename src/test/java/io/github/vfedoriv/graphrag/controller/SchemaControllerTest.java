package io.github.vfedoriv.graphrag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaRequest;
import io.github.vfedoriv.graphrag.service.SchemaGenerationService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SchemaControllerTest {

    @Test
    void generateSchemaReturnsYamlContent() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        when(generationService.generateYaml("generated-legal-schema", 1, "from text", "raw input text"))
            .thenReturn("name: generated-legal-schema\nversion: 1\nnodes: []\nrelationships: []\n");

        SchemaController controller = new SchemaController(registryService, generationService);
        var response = controller.generateSchema(
            new GenerateSchemaRequest("generated-legal-schema", 1, "from text", "raw input text", false)
        );

        assertThat(response.content()).contains("name: generated-legal-schema");
        assertThat(response.content()).contains("nodes:");
        assertThat(response.content()).contains("relationships:");
        assertThat(response.schemaId()).isNull();
    }

    @Test
    void generateSchemaSavesWhenFlagIsTrue() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        String yaml = "name: generated-legal-schema\nversion: 1\nnodes: []\nrelationships: []\n";
        when(generationService.generateYaml("generated-legal-schema", 1, "from text", "raw input text"))
            .thenReturn(yaml);
        SchemaDefinitionNode saved = new SchemaDefinitionNode();
        saved.setId("schema-123");
        saved.setName("generated-legal-schema");
        saved.setVersion(1);
        saved.setSourceType(SchemaSourceType.GENERATED);
        saved.setCreatedAt(Instant.now());
        when(registryService.createSchema(eq(yaml), eq(SchemaSourceType.GENERATED))).thenReturn(saved);

        SchemaController controller = new SchemaController(registryService, generationService);
        var response = controller.generateSchema(
            new GenerateSchemaRequest("generated-legal-schema", 1, "from text", "raw input text", true)
        );

        assertThat(response.content()).isEqualTo(yaml);
        assertThat(response.schemaId()).isEqualTo("schema-123");
        verify(registryService).createSchema(eq(yaml), eq(SchemaSourceType.GENERATED));
    }
}
