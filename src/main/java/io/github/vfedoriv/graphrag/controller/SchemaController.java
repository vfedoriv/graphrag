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
import io.github.vfedoriv.graphrag.dto.SchemaGenerationResult;
import io.github.vfedoriv.graphrag.dto.SchemaDetailsResponse;
import io.github.vfedoriv.graphrag.dto.SchemaResponse;
import io.github.vfedoriv.graphrag.dto.SchemaValidationResponse;
import io.github.vfedoriv.graphrag.dto.UpdateSchemaRequest;
import io.github.vfedoriv.graphrag.dto.ValidateSchemaRequest;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
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
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    @Operation(summary = "Create schema", description = "Registers a new immutable schema version from JSON content.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Schema created",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SchemaResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    name = "Created schema",
                    value = "{\"id\":\"schema-01\",\"name\":\"legal-contracts\",\"version\":1,\"sourceType\":\"GENERATED\",\"format\":\"JSON\",\"contentHash\":\"a74f9f7fbb\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-05-03T10:12:00Z\"}"
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid schema payload", content = @Content(schema = @Schema()))
    })
    public SchemaResponse createSchema(@Valid @RequestBody CreateSchemaRequest request) {
        log.info(
            "Create schema request: sourceType={}, knowledgeBaseId={}, contentLength={}",
            request.sourceType(),
            request.knowledgeBaseId(),
            LogSanitizer.length(request.content())
        );
        SchemaResponse response = toResponse(schemaRegistryService.createSchema(request.content(), request.sourceType(), request.knowledgeBaseId()));
        log.info(
            "Create schema completed: schemaId={}, name={}, version={}, status={}",
            response.id(),
            response.name(),
            response.version(),
            response.status()
        );
        return response;
    }

    @PostMapping("/schemas/generate")
    @Operation(summary = "Generate schema JSON", description = "Generates a new graph schema JSON from unstructured text.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema JSON generated"),
        @ApiResponse(responseCode = "400", description = "Invalid generation request", content = @Content(schema = @Schema()))
    })
    public GenerateSchemaResponse generateSchema(@Valid @RequestBody GenerateSchemaRequest request) {
        log.info(
            "Generate schema request: name={}, version={}, descriptionPresent={}, textLength={}, exampleLength={}",
            request.name(),
            request.version(),
            request.description() != null && !request.description().isBlank(),
            LogSanitizer.length(request.text()),
            LogSanitizer.length(request.example())
        );
        SchemaGenerationResult result = schemaGenerationService.generate(
            request.name(),
            request.version(),
            request.description(),
            request.text(),
            request.example()
        );
        log.info(
            "Generate schema completed: name={}, version={}, jsonLength={}, warningCount={}",
            request.name(),
            request.version(),
            result.content().length(),
            result.warnings().size()
        );
        return new GenerateSchemaResponse(result.content(), result.warnings());
    }

    @PostMapping(path = "/schemas/generate/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Generate schema JSON from uploaded file",
        description = "Parses an uploaded file (PDF/TXT/DOCX) and generates a graph schema JSON from extracted text."
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
        @ApiResponse(responseCode = "200", description = "Schema JSON generated"),
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
        log.info("Generate schema from file parsed text: fileName='{}', textLength={}", file.getOriginalFilename(), text.length());
        SchemaGenerationResult result = schemaGenerationService.generate(
            request.name(),
            request.version(),
            request.description(),
            text,
            normalizeExample(request.example())
        );
        log.info(
            "Generate schema from file completed: name={}, version={}, jsonLength={}, warningCount={}",
            request.name(),
            request.version(),
            result.content().length(),
            result.warnings().size()
        );
        return new GenerateSchemaResponse(result.content(), result.warnings());
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/schemas/generate")
    @Operation(summary = "Generate schema JSON for knowledge base", description = "Generates graph schema JSON using the knowledge base active AI profile.")
    public GenerateSchemaResponse generateSchemaForKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Valid @RequestBody GenerateSchemaRequest request
    ) {
        SchemaGenerationResult result = schemaGenerationService.generate(
            knowledgeBaseId,
            request.name(),
            request.version(),
            request.description(),
            request.text(),
            request.example()
        );
        return new GenerateSchemaResponse(result.content(), result.warnings());
    }

    @PostMapping(path = "/knowledge-bases/{knowledgeBaseId}/schemas/generate/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Generate schema JSON from file for knowledge base", description = "Parses a file and generates graph schema JSON using the knowledge base active AI profile.")
    public GenerateSchemaResponse generateSchemaFromFileForKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Parameter(description = "Schema generation request metadata JSON part")
        @Valid @RequestPart("request") GenerateSchemaFromFileRequest request,
        @Parameter(description = "Source file used for schema generation") @RequestPart("file") MultipartFile file
    ) {
        String text = parseUploadedText(file);
        SchemaGenerationResult result = schemaGenerationService.generate(
            knowledgeBaseId,
            request.name(),
            request.version(),
            request.description(),
            text,
            normalizeExample(request.example())
        );
        return new GenerateSchemaResponse(result.content(), result.warnings());
    }

    private String normalizeExample(Object exampleValue) {
        if (exampleValue instanceof String stringValue) {
            return stringValue;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(exampleValue);
        } catch (JsonProcessingException e) {
            log.error("Failed to normalize schema generation example: message={}", e.getMessage(), e);
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
        log.info(
            "Generate schema example request: textLength={}, userPromptLength={}",
            LogSanitizer.length(request.text()),
            LogSanitizer.length(request.userPrompt())
        );
        String example = schemaGenerationService.generateExample(request.text(), request.userPrompt());
        log.info("Generate schema example completed: exampleLength={}", example.length());
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
        log.info(
            "Generate schema example from file request: userPromptLength={}, fileName='{}', fileContentType='{}', fileSize={}",
            LogSanitizer.length(userPrompt),
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize()
        );
        String text = parseUploadedText(file);
        log.info("Generate schema example from file parsed text: fileName='{}', textLength={}", file.getOriginalFilename(), text.length());
        String example = schemaGenerationService.generateExample(text, userPrompt);
        log.info("Generate schema example from file completed: fileName='{}', exampleLength={}", file.getOriginalFilename(), example.length());
        return new GenerateSchemaExampleResponse(example);
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/schemas/generate/example")
    @Operation(summary = "Generate schema example for knowledge base", description = "Generates a schema example using the knowledge base active AI profile.")
    public GenerateSchemaExampleResponse generateSchemaExampleForKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Valid @RequestBody GenerateSchemaExampleRequest request
    ) {
        String example = schemaGenerationService.generateExample(knowledgeBaseId, request.text(), request.userPrompt());
        return new GenerateSchemaExampleResponse(example);
    }

    @PostMapping(path = "/knowledge-bases/{knowledgeBaseId}/schemas/generate/example/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Generate schema example from file for knowledge base", description = "Parses a file and generates a schema example using the knowledge base active AI profile.")
    public GenerateSchemaExampleResponse generateSchemaExampleFromFileForKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Parameter(description = "Optional guidance for domain/entities/relationships/properties")
        @RequestParam(required = false) String userPrompt,
        @Parameter(description = "Source file used for example generation") @RequestPart("file") MultipartFile file
    ) {
        String text = parseUploadedText(file);
        String example = schemaGenerationService.generateExample(knowledgeBaseId, text, userPrompt);
        return new GenerateSchemaExampleResponse(example);
    }

    @GetMapping("/schemas")
    @Operation(summary = "List schemas", description = "Returns all known schema versions.")
    @ApiResponse(responseCode = "200", description = "Schemas retrieved")
    public List<SchemaResponse> listSchemas() {
        log.info("List schemas request");
        List<SchemaResponse> response = schemaRegistryService.listSchemas().stream().map(this::toResponse).toList();
        log.info("List schemas completed: count={}", response.size());
        return response;
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/schemas")
    @Operation(summary = "List schemas by knowledge base", description = "Returns schemas associated with a knowledge base.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schemas retrieved"),
        @ApiResponse(responseCode = "404", description = "Knowledge base not found", content = @Content(schema = @Schema()))
    })
    public List<SchemaResponse> listSchemasByKnowledgeBase(
        @Parameter(description = "Knowledge base identifier") @PathVariable @NotBlank String knowledgeBaseId
    ) {
        log.info("List schemas by knowledge base request: knowledgeBaseId={}", knowledgeBaseId);
        List<SchemaResponse> response = schemaRegistryService.listSchemasByKnowledgeBase(knowledgeBaseId).stream().map(this::toResponse).toList();
        log.info("List schemas by knowledge base completed: knowledgeBaseId={}, count={}", knowledgeBaseId, response.size());
        return response;
    }

    @GetMapping("/schemas/{schemaId}")
    @Operation(summary = "Get schema by ID", description = "Returns details for a schema version.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema found"),
        @ApiResponse(responseCode = "404", description = "Schema not found", content = @Content(schema = @Schema()))
    })
    public SchemaDetailsResponse getSchema(@Parameter(description = "Schema identifier") @PathVariable String schemaId) {
        log.info("Get schema request: schemaId={}", schemaId);
        SchemaDetailsResponse response = toDetailsResponse(schemaRegistryService.getSchema(schemaId));
        log.info("Get schema completed: schemaId={}, name={}, version={}", response.id(), response.name(), response.version());
        return response;
    }

    @PutMapping("/schemas/{schemaId}")
    @Operation(summary = "Update schema by ID", description = "Replaces content for an existing inactive schema while preserving name and version.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema updated"),
        @ApiResponse(responseCode = "400", description = "Invalid schema payload", content = @Content(schema = @Schema())),
        @ApiResponse(responseCode = "404", description = "Schema not found", content = @Content(schema = @Schema())),
        @ApiResponse(responseCode = "409", description = "Schema identity conflict or active schema", content = @Content(schema = @Schema()))
    })
    public SchemaDetailsResponse updateSchema(
        @Parameter(description = "Schema identifier") @PathVariable String schemaId,
        @Valid @RequestBody UpdateSchemaRequest request
    ) {
        log.info(
            "Update schema request: schemaId={}, sourceType={}, contentLength={}",
            schemaId,
            request.sourceType(),
            LogSanitizer.length(request.content())
        );
        SchemaDetailsResponse response = toDetailsResponse(schemaRegistryService.updateSchema(schemaId, request.content(), request.sourceType()));
        log.info("Update schema completed: schemaId={}, name={}, version={}", response.id(), response.name(), response.version());
        return response;
    }

    @DeleteMapping("/schemas/{schemaId}")
    @Operation(summary = "Delete schema by ID", description = "Deletes an inactive schema and detaches knowledge-base schema relationships.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Schema deleted"),
        @ApiResponse(responseCode = "404", description = "Schema not found", content = @Content(schema = @Schema())),
        @ApiResponse(responseCode = "409", description = "Schema is active", content = @Content(schema = @Schema()))
    })
    public ResponseEntity<Void> deleteSchema(@Parameter(description = "Schema identifier") @PathVariable String schemaId) {
        log.info("Delete schema request: schemaId={}", schemaId);
        schemaRegistryService.deleteSchema(schemaId);
        log.info("Delete schema completed: schemaId={}", schemaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/schemas/validate")
    @Operation(summary = "Validate schema JSON", description = "Validates JSON payload against schema format rules.")
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
            description = "Schema JSON to validate.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ValidateSchemaRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = "{\"content\":\"{\\\"name\\\":\\\"legal-contracts\\\",\\\"version\\\":1,\\\"nodes\\\":[{\\\"label\\\":\\\"Contract\\\",\\\"key\\\":\\\"contractId\\\"}],\\\"relationships\\\":[]}\"}"
                )
            )
        )
        @Valid @RequestBody ValidateSchemaRequest request
    ) {
        log.info("Validate schema request: contentLength={}", LogSanitizer.length(request.content()));
        List<String> errors = schemaRegistryService.validateJson(request.content());
        log.info("Validate schema completed: valid={}, errors={}", errors.isEmpty(), errors.size());
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
        log.info("Activate schema request: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
        schemaRegistryService.activateSchema(knowledgeBaseId, schemaId);
        log.info("Activate schema completed: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach")
    @Operation(summary = "Attach schema to knowledge base", description = "Associates a schema with a knowledge base without activating it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema attached"),
        @ApiResponse(responseCode = "404", description = "Schema or knowledge base not found", content = @Content(schema = @Schema()))
    })
    public void attachSchema(
        @Parameter(description = "Knowledge base identifier") @PathVariable String knowledgeBaseId,
        @Parameter(description = "Schema identifier to attach") @PathVariable String schemaId
    ) {
        log.info("Attach schema request: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
        schemaRegistryService.attachSchema(knowledgeBaseId, schemaId);
        log.info("Attach schema completed: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
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

    private SchemaDetailsResponse toDetailsResponse(SchemaDefinitionNode node) {
        return new SchemaDetailsResponse(
            node.getId(),
            node.getName(),
            node.getVersion(),
            node.getSourceType(),
            node.getFormat(),
            node.getContent(),
            node.getContentHash(),
            node.getStatus(),
            node.getCreatedAt()
        );
    }

    private String parseUploadedText(MultipartFile file) {
        try {
            return documentParsingService.parse(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            log.error("Failed to read uploaded file: filename={}, message={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new IllegalArgumentException("Failed to read uploaded file", e);
        }
    }

}
