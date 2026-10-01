package io.github.vfedoriv.graphrag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.dto.CreateSchemaRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaExampleResponse;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaResponse;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaFromFileRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaExampleRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaRequest;
import io.github.vfedoriv.graphrag.dto.SchemaGenerationResult;
import io.github.vfedoriv.graphrag.dto.SchemaGenerationWarning;
import io.github.vfedoriv.graphrag.dto.SchemaDetailsResponse;
import io.github.vfedoriv.graphrag.dto.SchemaResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse;
import io.github.vfedoriv.graphrag.dto.UpdateSchemaRequest;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.service.SchemaGenerationService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import io.github.vfedoriv.graphrag.service.SchemaDiscoveryService;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.ResponseStatus;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void discoverSchemaDelegatesJsonSourcesWithoutChangingGenerationContract() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        SchemaDiscoveryService discoveryService = Mockito.mock(SchemaDiscoveryService.class);
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(List.of("document-1"),
            List.of(new SchemaDiscoveryRequest.TextSource("sample", "text")), null,
            SchemaDiscoveryRequest.DiscoveryGuidance.empty());
        SchemaDiscoveryResponse expected = new SchemaDiscoveryResponse(ResponseStatus.COMPLETED, List.of(), List.of(),
            List.of(), List.of(), OBJECT_MAPPER.createObjectNode(), null);
        when(discoveryService.discover("kb-01", request, List.of())).thenReturn(expected);
        SchemaController controller = new SchemaController(registryService, generationService, parsingService, discoveryService);

        assertThat(controller.discoverSchema("kb-01", request)).isSameAs(expected);
        verify(discoveryService).discover("kb-01", request, List.of());
    }

    @Test
    void discoverSchemaFromFilesDelegatesMixedMetadataAndFiles() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        SchemaDiscoveryService discoveryService = Mockito.mock(SchemaDiscoveryService.class);
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(List.of("document-1"), List.of(), null,
            SchemaDiscoveryRequest.DiscoveryGuidance.empty());
        MockMultipartFile first = new MockMultipartFile("files", "first.txt", "text/plain", "first".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "second.txt", "text/plain", "second".getBytes());
        SchemaDiscoveryResponse expected = new SchemaDiscoveryResponse(ResponseStatus.PARTIAL, List.of(), List.of(),
            List.of(), List.of(), OBJECT_MAPPER.createObjectNode(), null);
        when(discoveryService.discover("kb-01", request, List.of(first, second))).thenReturn(expected);
        SchemaController controller = new SchemaController(registryService, generationService, parsingService, discoveryService);

        assertThat(controller.discoverSchemaFromFiles("kb-01", request, List.of(first, second))).isSameAs(expected);
        verify(discoveryService).discover("kb-01", request, List.of(first, second));
    }

    @Test
    void generateSchemaReturnsJsonContent() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        when(generationService.generate("generated-legal-schema", 1, "from text", "raw input text", "example json"))
            .thenReturn(new SchemaGenerationResult(
                "{\"name\":\"generated-legal-schema\",\"version\":1,\"nodes\":[],\"relationships\":[]}",
                List.of()
            ));

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
        when(generationService.generate("generated-legal-schema", 2, "from file", "parsed text", "\"example json\""))
            .thenReturn(new SchemaGenerationResult(json, List.of()));

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        GenerateSchemaResponse response = controller.generateSchemaFromFile(
            new GenerateSchemaFromFileRequest("generated-legal-schema", 2, "from file", readJson("\"example json\"")),
            file
        );

        assertThat(response.content()).isEqualTo(json);
        verify(parsingService).parse(eq("sample.txt"), eq("text/plain"), argThat(bytes -> Arrays.equals(bytes, rawBytes)));
        verify(generationService).generate("generated-legal-schema", 2, "from file", "parsed text", "\"example json\"");
    }

    @Test
    void generateSchemaReturnsStructuredWarningsWithoutFailure() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        when(generationService.generate("generated-legal-schema", 1, "from text", "raw input text", "example json"))
            .thenReturn(new SchemaGenerationResult(
                "{\"name\":\"generated-legal-schema\",\"version\":1,\"nodes\":[],\"relationships\":[]}",
                List.of(new SchemaGenerationWarning(
                    0,
                    "Person",
                    "NODE_KEY_PROPERTY_MISMATCH",
                    "Node key 'id' is not declared in properties for node 'Person'",
                    List.of("Add property 'id' to node properties", "Change node key to an existing property name")
                ))
            ));

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        GenerateSchemaResponse response = controller.generateSchema(
            new GenerateSchemaRequest("generated-legal-schema", 1, "from text", "raw input text", "example json")
        );

        assertThat(response.content()).contains("\"name\":\"generated-legal-schema\"");
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().getFirst().nodeIndex()).isEqualTo(0);
        assertThat(response.warnings().getFirst().nodeLabel()).isEqualTo("Person");
        assertThat(response.warnings().getFirst().code()).isEqualTo("NODE_KEY_PROPERTY_MISMATCH");
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

        assertThat(response.value()).isEqualTo("[{\"head\":\"Acme\"}]");
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

        assertThat(response.value()).isEqualTo("[{\"head\":\"Acme\"}]");
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
    void updateSchemaReturnsDetailsAndDelegatesToRegistry() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        String content = "{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[],\"relationships\":[]}";
        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-01");
        schema.setName("legal-contracts");
        schema.setVersion(1);
        schema.setSourceType(SchemaSourceType.GENERATED);
        schema.setFormat(SchemaFormat.JSON);
        schema.setContent(content);
        schema.setContentHash("hash-updated");
        schema.setStatus(SchemaStatus.INACTIVE);
        schema.setCreatedAt(Instant.parse("2026-05-03T10:12:00Z"));

        when(registryService.updateSchema("schema-01", content, SchemaSourceType.GENERATED)).thenReturn(schema);

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        SchemaDetailsResponse response = controller.updateSchema(
            "schema-01",
            new UpdateSchemaRequest(content, SchemaSourceType.GENERATED)
        );

        assertThat(response.id()).isEqualTo("schema-01");
        assertThat(response.content()).isEqualTo(content);
        assertThat(response.contentHash()).isEqualTo("hash-updated");
        verify(registryService).updateSchema("schema-01", content, SchemaSourceType.GENERATED);
    }

    @Test
    void deleteSchemaReturnsNoContentAndDelegatesToRegistry() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        ResponseEntity<Void> response = controller.deleteSchema("schema-01");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
        verify(registryService).deleteSchema("schema-01");
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

        when(registryService.createSchema(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(schema);
        when(registryService.listSchemas()).thenReturn(List.of(schema));

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        SchemaResponse created = controller.createSchema(
            new CreateSchemaRequest(
                "{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[],\"relationships\":[]}",
                SchemaSourceType.PREDEFINED,
                "kb-01"
            )
        );
        List<SchemaResponse> listed = controller.listSchemas();

        assertThat(created).isInstanceOf(SchemaResponse.class);
        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst()).isInstanceOf(SchemaResponse.class);
        assertThat(Arrays.stream(SchemaResponse.class.getDeclaredMethods()).map(method -> method.getName()))
            .doesNotContain("content");
        verify(registryService).createSchema(
            "{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[],\"relationships\":[]}",
            SchemaSourceType.PREDEFINED,
            "kb-01"
        );
    }

    @Test
    void attachSchemaDelegatesToRegistryService() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);

        controller.attachSchema("kb-01", "schema-01");

        verify(registryService).attachSchema("kb-01", "schema-01");
    }

    @Test
    void listSchemasByKnowledgeBaseReturnsAssociatedSchemas() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);

        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-01");
        schema.setName("legal-contracts");
        schema.setVersion(1);
        schema.setSourceType(SchemaSourceType.PREDEFINED);
        schema.setFormat(SchemaFormat.JSON);
        schema.setContentHash("hash-01");
        schema.setStatus(SchemaStatus.ACTIVE);
        schema.setCreatedAt(Instant.parse("2026-05-03T10:12:00Z"));
        when(registryService.listSchemasByKnowledgeBase("kb-01")).thenReturn(List.of(schema));

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        List<SchemaResponse> response = controller.listSchemasByKnowledgeBase("kb-01");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo("schema-01");
        verify(registryService).listSchemasByKnowledgeBase("kb-01");
    }

    @Test
    void listSchemasByKnowledgeBaseReturnsEmptyList() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        when(registryService.listSchemasByKnowledgeBase("kb-empty")).thenReturn(List.of());

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);
        List<SchemaResponse> response = controller.listSchemasByKnowledgeBase("kb-empty");

        assertThat(response).isEmpty();
        verify(registryService).listSchemasByKnowledgeBase("kb-empty");
    }

    @Test
    void listSchemasByKnowledgeBaseUnknownKnowledgeBasePropagatesNotFound() {
        SchemaRegistryService registryService = Mockito.mock(SchemaRegistryService.class);
        SchemaGenerationService generationService = Mockito.mock(SchemaGenerationService.class);
        DocumentParsingService parsingService = Mockito.mock(DocumentParsingService.class);
        when(registryService.listSchemasByKnowledgeBase("missing-kb"))
            .thenThrow(new NotFoundException("Knowledge base not found: missing-kb"));

        SchemaController controller = new SchemaController(registryService, generationService, parsingService);

        assertThatThrownBy(() -> controller.listSchemasByKnowledgeBase("missing-kb"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
    }

    private static com.fasterxml.jackson.databind.JsonNode readJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
