package io.github.vfedoriv.graphrag.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.dto.CreateSchemaRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaExampleRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaExampleResponse;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaFromFileRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaRequest;
import io.github.vfedoriv.graphrag.dto.GenerateSchemaResponse;
import io.github.vfedoriv.graphrag.dto.SchemaResponse;
import io.github.vfedoriv.graphrag.dto.SchemaValidationResponse;
import io.github.vfedoriv.graphrag.dto.ValidateSchemaRequest;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.service.SchemaGenerationService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Schemas", description = "Schema lifecycle operations: create, list, validate, and activate.")
@Slf4j
public class SchemaController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SchemaRegistryService schemaRegistryService;
    private final SchemaGenerationService schemaGenerationService;
    private final DocumentParsingService documentParsingService;

    public SchemaController(
        SchemaRegistryService schemaRegistryService,
        SchemaGenerationService schemaGenerationService,
        DocumentParsingService documentParsingService
    ) {
        this.schemaRegistryService = schemaRegistryService;
        this.schemaGenerationService = schemaGenerationService;
        this.documentParsingService = documentParsingService;
    }

    @PostMapping("/schemas")
    @Operation(summary = "Create schema", description = "Registers a new immutable schema version from YAML content.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Schema created",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SchemaResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    name = "Created schema",
                    value = "{\"id\":\"schema-01\",\"name\":\"legal-contracts\",\"version\":1,\"sourceType\":\"USER_DEFINED\",\"format\":\"YAML\",\"contentHash\":\"a74f9f7fbb\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-05-03T10:12:00Z\"}"
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid schema payload", content = @Content(schema = @Schema()))
    })
    public SchemaResponse createSchema(@Valid @RequestBody CreateSchemaRequest request) {
        return toResponse(schemaRegistryService.createSchema(request.content(), request.sourceType()));
    }

    @PostMapping("/schemas/generate")
    @Operation(summary = "Generate schema YAML", description = "Generates a new graph schema YAML from unstructured text.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema YAML generated"),
        @ApiResponse(responseCode = "400", description = "Invalid generation request", content = @Content(schema = @Schema()))
    })
    public GenerateSchemaResponse generateSchema(@Valid @RequestBody GenerateSchemaRequest request) {
        String yaml = schemaGenerationService.generateYaml(
            request.name(),
            request.version(),
            request.description(),
            request.text(),
            request.example()
        );
        return new GenerateSchemaResponse(yaml);
    }

    @PostMapping(path = "/schemas/generate/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Generate schema YAML from uploaded file",
        description = "Parses an uploaded file (PDF/TXT/DOCX) and generates a graph schema YAML from extracted text."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            encoding = {
                @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE),
                @Encoding(name = "file", contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            }
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema YAML generated"),
        @ApiResponse(responseCode = "400", description = "Invalid generation request", content = @Content(schema = @Schema()))
    })
    public GenerateSchemaResponse generateSchemaFromFile(
        @Parameter(description = "Schema generation request metadata JSON part")
        @Valid @RequestPart("request") GenerateSchemaFromFileRequest request,
        @Parameter(description = "Source file used for schema generation") @RequestPart("file") MultipartFile file
    ) {
        log.info(
            "generateSchemaFromFile called: name='{}', version={}, descriptionPresent={}, exampleType={}, fileName='{}', fileContentType='{}', fileSize={}",
            request.name(),
            request.version(),
            request.description() != null && !request.description().isBlank(),
            request.example() == null ? "null" : request.example().getClass().getName(),
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize()
        );
        String text = parseUploadedText(file);
        String yaml = schemaGenerationService.generateYaml(
            request.name(),
            request.version(),
            request.description(),
            text,
            normalizeExample(request.example())
        );
        return new GenerateSchemaResponse(yaml);
    }

    private String normalizeExample(Object exampleValue) {
        if (exampleValue instanceof String stringValue) {
            return stringValue;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(exampleValue);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("request.example: invalid JSON", e);
        }
    }

    @PostMapping("/schemas/generate/example")
    @Operation(
        summary = "Generate schema example from text",
        description = "Generates representative entities and relationships from text to guide schema generation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema example generated"),
        @ApiResponse(responseCode = "400", description = "Invalid generation request", content = @Content(schema = @Schema()))
    })
    public GenerateSchemaExampleResponse generateSchemaExample(@Valid @RequestBody GenerateSchemaExampleRequest request) {
        String example = schemaGenerationService.generateExample(request.text(), request.userPrompt());
        return new GenerateSchemaExampleResponse(example);
    }

    @PostMapping(path = "/schemas/generate/example/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Generate schema example from uploaded file",
        description = "Parses an uploaded file (PDF/TXT/DOCX) and generates representative entities and relationships to guide schema generation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema example generated"),
        @ApiResponse(responseCode = "400", description = "Invalid generation request", content = @Content(schema = @Schema()))
    })
    public GenerateSchemaExampleResponse generateSchemaExampleFromFile(
        @Parameter(description = "Optional guidance for domain/entities/relationships/properties")
        @RequestParam(required = false) String userPrompt,
        @Parameter(description = "Source file used for example generation") @RequestPart("file") MultipartFile file
    ) {
        String text = parseUploadedText(file);
        String example = schemaGenerationService.generateExample(text, userPrompt);
        return new GenerateSchemaExampleResponse(example);
    }

    @GetMapping("/schemas")
    @Operation(summary = "List schemas", description = "Returns all known schema versions.")
    @ApiResponse(responseCode = "200", description = "Schemas retrieved")
    public List<SchemaResponse> listSchemas() {
        return schemaRegistryService.listSchemas().stream().map(this::toResponse).toList();
    }

    @GetMapping("/schemas/{schemaId}")
    @Operation(summary = "Get schema by ID", description = "Returns details for a schema version.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema found"),
        @ApiResponse(responseCode = "404", description = "Schema not found", content = @Content(schema = @Schema()))
    })
    public SchemaResponse getSchema(@Parameter(description = "Schema identifier") @PathVariable String schemaId) {
        return toResponse(schemaRegistryService.getSchema(schemaId));
    }

    @PostMapping("/schemas/validate")
    @Operation(summary = "Validate schema YAML", description = "Validates YAML payload against schema format rules.")
    @ApiResponse(
        responseCode = "200",
        description = "Validation result returned",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = SchemaValidationResponse.class),
            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                value = "{\"valid\":true,\"errors\":[]}"
            )
        )
    )
    public SchemaValidationResponse validateSchema(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Schema YAML to validate.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ValidateSchemaRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"content\":\"name: legal-contracts\\nversion: 1\\nnodes:\\n  - label: Contract\\n    key: contractId\\nrelationships: []\"}"
                )
            )
        )
        @Valid @RequestBody ValidateSchemaRequest request
    ) {
        List<String> errors = schemaRegistryService.validateYaml(request.content());
        return new SchemaValidationResponse(errors.isEmpty(), errors);
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/activate")
    @Operation(summary = "Activate schema for knowledge base", description = "Sets the active schema used by extraction and query generation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema activated"),
        @ApiResponse(responseCode = "404", description = "Schema or knowledge base not found", content = @Content(schema = @Schema()))
    })
    public void activateSchema(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Parameter(description = "Schema identifier to activate") @PathVariable String schemaId
    ) {
        schemaRegistryService.activateSchema(knowledgeBaseId, schemaId);
    }

    private SchemaResponse toResponse(SchemaDefinitionNode node) {
        return new SchemaResponse(
            node.getId(),
            node.getName(),
            node.getVersion(),
            node.getSourceType(),
            node.getFormat(),
            node.getContentHash(),
            node.getStatus(),
            node.getCreatedAt()
        );
    }

    private String parseUploadedText(MultipartFile file) {
        try {
            return documentParsingService.parse(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file", e);
        }
    }
}
