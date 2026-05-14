package io.github.vfedoriv.graphrag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaExampleResponse;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaResponse;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaFromFileRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaExampleRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDetailsResponse;
import io.github.vfedoriv.graphrag.dto.SchemaResponse;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.service.SchemaGenerationService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void generateSchemaReturnsJsonContent() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        when(generationService.generateJson("generated-legal-schema", 1, "from text", "raw input text", "example json"))
            .thenReturn("{\"name\":\"generated-legal-schema\",\"version\":1,\"nodes\":[],\"relationships\":[]}");

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        GenerateSchemaResponse response = controller.generateSchema(
            new GenerateSchemaRequest("generated-legal-schema", 1, "from text", "raw input text", "example json")
        );

        assertThat(response.content()).contains("\"name\":\"generated-legal-schema\"");
        assertThat(response.content()).contains("\"nodes\"");
        assertThat(response.content()).contains("\"relationships\"");
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
        String json = "{\"name\":\"generated-legal-schema\",\"version\":2,\"nodes\":[],\"relationships\":[]}";
        when(generationService.generateJson("generated-legal-schema", 2, "from file", "parsed text", "\"example json\""))
            .thenReturn(json);

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        GenerateSchemaResponse response = controller.generateSchemaFromFile(
            new GenerateSchemaFromFileRequest("generated-legal-schema", 2, "from file", readJson("\"example json\"")),
            file
        );

        assertThat(response.content()).isEqualTo(json);
        verify(parsingService).parse(eq("sample.txt"), eq("text/plain"), argThat(bytes -> Arrays.equals(bytes, rawBytes)));
        verify(generationService).generateJson("generated-legal-schema", 2, "from file", "parsed text", "\"example json\"");
    }

    @Test
    void generateSchemaExampleUsesTextAndPrompt() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        when(generationService.generateExample("raw input text", "focus on contracts")).thenReturn("[{\"head\":\"Acme\"}]");

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        GenerateSchemaExampleResponse response =
            controller.generateSchemaExample(new GenerateSchemaExampleRequest("raw input text", "focus on contracts"));

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
        GenerateSchemaExampleResponse response = controller.generateSchemaExampleFromFile(null, file);

        assertThat(response.example()).isEqualTo("[{\"head\":\"Acme\"}]");
        verify(generationService).generateExample("parsed text", null);
    }

    @Test
    void getSchemaReturnsPersistedSchemaContent() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-01");
        schema.setName("legal-contracts");
        schema.setVersion(1);
        schema.setSourceType(SchemaSourceType.PREDEFINED);
        schema.setFormat(SchemaFormat.JSON);
        schema.setContent("{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[],\"relationships\":[]}");
        schema.setContentHash("hash-01");
        schema.setStatus(SchemaStatus.ACTIVE);
        schema.setCreatedAt(Instant.parse("2026-05-03T10:12:00Z"));

        when(registryService.getSchema("schema-01")).thenReturn(schema);

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        SchemaDetailsResponse response = controller.getSchema("schema-01");

        assertThat(response.id()).isEqualTo("schema-01");
        assertThat(response.content()).isEqualTo("{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[],\"relationships\":[]}");
        assertThat(response.contentHash()).isEqualTo("hash-01");
        verify(registryService).getSchema("schema-01");
    }

    @Test
    void getSchemaNotFoundPropagatesException() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        when(registryService.getSchema("missing-schema")).thenThrow(new NotFoundException("Schema not found: missing-schema"));

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);

        assertThatThrownBy(() -> controller.getSchema("missing-schema"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing-schema");
    }

    @Test
    void createAndListSchemasUseSummaryResponseWithoutContentField() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-01");
        schema.setName("legal-contracts");
        schema.setVersion(1);
        schema.setSourceType(SchemaSourceType.PREDEFINED);
        schema.setFormat(SchemaFormat.JSON);
        schema.setContent("{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[],\"relationships\":[]}");
        schema.setContentHash("hash-01");
        schema.setStatus(SchemaStatus.ACTIVE);
        schema.setCreatedAt(Instant.parse("2026-05-03T10:12:00Z"));

        when(registryService.createSchema(Mockito.anyString(), Mockito.any())).thenReturn(schema);
        when(registryService.listSchemas()).thenReturn(List.of(schema));

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        SchemaResponse created = controller.createSchema(
            new io.github.vfedoriv.graphrag.dto.CreateSchemaRequest(
                "{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[],\"relationships\":[]}",
                SchemaSourceType.PREDEFINED
            )
        );
        List<SchemaResponse> listed = controller.listSchemas();

        assertThat(created).isInstanceOf(SchemaResponse.class);
        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst()).isInstanceOf(SchemaResponse.class);
        assertThat(Arrays.stream(SchemaResponse.class.getDeclaredMethods()).map(method -> method.getName()))
            .doesNotContain("content");
    }

    private static com.fasterxml.jackson.databind.JsonNode readJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
