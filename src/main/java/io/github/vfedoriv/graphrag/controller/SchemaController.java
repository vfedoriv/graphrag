package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.dto.CreateSchemaRequest;
import io.github.vfedoriv.graphrag.dto.SchemaResponse;
import io.github.vfedoriv.graphrag.dto.SchemaValidationResponse;
import io.github.vfedoriv.graphrag.dto.ValidateSchemaRequest;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SchemaController {

    private final SchemaRegistryService schemaRegistryService;

    public SchemaController(SchemaRegistryService schemaRegistryService) {
        this.schemaRegistryService = schemaRegistryService;
    }

    @PostMapping("/schemas")
    public SchemaResponse createSchema(@Valid @RequestBody CreateSchemaRequest request) {
        return toResponse(schemaRegistryService.createSchema(request.content(), request.sourceType()));
    }

    @GetMapping("/schemas")
    public List<SchemaResponse> listSchemas() {
        return schemaRegistryService.listSchemas().stream().map(this::toResponse).toList();
    }

    @GetMapping("/schemas/{schemaId}")
    public SchemaResponse getSchema(@PathVariable String schemaId) {
        return toResponse(schemaRegistryService.getSchema(schemaId));
    }

    @PostMapping("/schemas/{schemaId}/validate")
    public SchemaValidationResponse validateSchema(@PathVariable String schemaId, @Valid @RequestBody ValidateSchemaRequest request) {
        List<String> errors = schemaRegistryService.validateYaml(request.content());
        return new SchemaValidationResponse(errors.isEmpty(), errors);
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/activate")
    public void activateSchema(@PathVariable String knowledgeBaseId, @PathVariable String schemaId) {
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
}
