package io.github.vfedoriv.graphrag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaFromFileRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaExampleRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaRequest;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.service.SchemaGenerationService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

class SchemaControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void generateSchemaReturnsYamlContent() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        when(generationService.generateYaml("generated-legal-schema", 1, "from text", "raw input text", "example json"))
            .thenReturn("name: generated-legal-schema\nversion: 1\nnodes: []\nrelationships: []\n");

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        var response = controller.generateSchema(
            new GenerateSchemaRequest("generated-legal-schema", 1, "from text", "raw input text", "example json")
        );

        assertThat(response.content()).contains("name: generated-legal-schema");
        assertThat(response.content()).contains("nodes:");
        assertThat(response.content()).contains("relationships:");
    }

    @Test
    void generateSchemaFromFileParsesAndGeneratesWithoutSaving() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "raw bytes".getBytes());
        byte[] rawBytes = "raw bytes".getBytes();
        when(parsingService.parse(eq("sample.txt"), eq("text/plain"), argThat(bytes -> Arrays.equals(bytes, rawBytes))))
            .thenReturn("parsed text");
        String yaml = "name: generated-legal-schema\nversion: 2\nnodes: []\nrelationships: []\n";
        when(generationService.generateYaml("generated-legal-schema", 2, "from file", "parsed text", "\"example json\""))
            .thenReturn(yaml);

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        var response = controller.generateSchemaFromFile(
            new GenerateSchemaFromFileRequest("generated-legal-schema", 2, "from file", readJson("\"example json\"")),
            file
        );

        assertThat(response.content()).isEqualTo(yaml);
        verify(parsingService).parse(eq("sample.txt"), eq("text/plain"), argThat(bytes -> Arrays.equals(bytes, rawBytes)));
        verify(generationService).generateYaml("generated-legal-schema", 2, "from file", "parsed text", "\"example json\"");
    }

    @Test
    void generateSchemaExampleUsesTextAndPrompt() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        when(generationService.generateExample("raw input text", "focus on contracts")).thenReturn("[{\"head\":\"Acme\"}]");

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        var response = controller.generateSchemaExample(new GenerateSchemaExampleRequest("raw input text", "focus on contracts"));

        assertThat(response.example()).isEqualTo("[{\"head\":\"Acme\"}]");
        verify(generationService).generateExample("raw input text", "focus on contracts");
    }

    @Test
    void generateSchemaExampleFromFileParsesAndUsesOptionalPrompt() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "raw bytes".getBytes());
        byte[] rawBytes = "raw bytes".getBytes();
        when(parsingService.parse(eq("sample.txt"), eq("text/plain"), argThat(bytes -> Arrays.equals(bytes, rawBytes))))
            .thenReturn("parsed text");
        when(generationService.generateExample("parsed text", null)).thenReturn("[{\"head\":\"Acme\"}]");

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        var response = controller.generateSchemaExampleFromFile(null, file);

        assertThat(response.example()).isEqualTo("[{\"head\":\"Acme\"}]");
        verify(generationService).generateExample("parsed text", null);
    }

    private static com.fasterxml.jackson.databind.JsonNode readJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
