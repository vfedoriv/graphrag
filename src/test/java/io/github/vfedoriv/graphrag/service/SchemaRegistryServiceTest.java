package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import io.github.vfedoriv.graphrag.schema.SchemaValidationException;
import io.github.vfedoriv.graphrag.schema.SchemaValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.neo4j.core.Neo4jClient;

class SchemaRegistryServiceTest {

    @Test
    void createSchemaPersistsNewSchemaWhenIdentityDoesNotExist() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        String json = schemaJson("contracts", 1, "Contract");
        when(schemaParser.parse(json)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());
        when(schemaRepository.existsByNameAndVersion("contracts", 1)).thenReturn(false);
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        SchemaDefinitionNode created = service.createSchema(json, SchemaSourceType.PREDEFINED);

        assertThat(created.getName()).isEqualTo("contracts");
        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getContent()).isEqualTo(json);
        assertThat(created.getContentHash()).hasSize(64);
        verify(schemaRepository).save(any(SchemaDefinitionNode.class));
    }

    @Test
    void createSchemaRejectsExistingIdentityWithoutSaving() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        String json = schemaJson("contracts", 1, "Contract");
        when(schemaParser.parse(json)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());
        when(schemaRepository.existsByNameAndVersion("contracts", 1)).thenReturn(true);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.createSchema(json, SchemaSourceType.PREDEFINED))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Schema version is immutable and already exists for name=contracts, version=1");
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void listSchemasByKnowledgeBaseReturnsAssociatedSchemas() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-01");

        when(knowledgeBaseRepository.existsById("kb-01")).thenReturn(true);
        when(schemaRepository.findAllByKnowledgeBaseId("kb-01")).thenReturn(List.of(schema));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        List<SchemaDefinitionNode> result = service.listSchemasByKnowledgeBase("kb-01");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo("schema-01");
        verify(knowledgeBaseRepository).existsById("kb-01");
        verify(schemaRepository).findAllByKnowledgeBaseId("kb-01");
    }

    @Test
    void listSchemasByKnowledgeBaseReturnsEmptyWhenNoAssociations() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        when(knowledgeBaseRepository.existsById("kb-empty")).thenReturn(true);
        when(schemaRepository.findAllByKnowledgeBaseId("kb-empty")).thenReturn(List.of());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        List<SchemaDefinitionNode> result = service.listSchemasByKnowledgeBase("kb-empty");

        assertThat(result).isEmpty();
        verify(knowledgeBaseRepository).existsById("kb-empty");
        verify(schemaRepository).findAllByKnowledgeBaseId("kb-empty");
    }

    @Test
    void listSchemasByKnowledgeBaseThrowsWhenKnowledgeBaseMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        when(knowledgeBaseRepository.existsById("missing-kb")).thenReturn(false);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.listSchemasByKnowledgeBase("missing-kb"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
    }

    @Test
    void updateSchemaPersistsReplacementContentAndHashForInactiveSchema() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        SchemaDefinitionNode existing = schemaNode("schema-01", "contracts", 1);
        String updatedJson = schemaJson("contracts", 1, "Agreement");
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(existing));
        when(schemaRepository.existsActiveKnowledgeBaseReference("schema-01")).thenReturn(false);
        when(schemaParser.parse(updatedJson)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        SchemaDefinitionNode updated = service.updateSchema("schema-01", updatedJson, SchemaSourceType.GENERATED);

        assertThat(updated.getId()).isEqualTo("schema-01");
        assertThat(updated.getContent()).isEqualTo(updatedJson);
        assertThat(updated.getContentHash()).hasSize(64);
        assertThat(updated.getSourceType()).isEqualTo(SchemaSourceType.GENERATED);
        assertThat(updated.getFormat()).isEqualTo(SchemaFormat.JSON);
        verify(schemaRepository).save(existing);
    }

    @Test
    void updateSchemaRejectsInvalidContentWithoutSaving() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        String invalidJson = schemaJson("contracts", 1, "Contract");
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(schemaRepository.existsActiveKnowledgeBaseReference("schema-01")).thenReturn(false);
        when(schemaParser.parse(invalidJson)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of("Node key must be declared as a property"));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.updateSchema("schema-01", invalidJson, SchemaSourceType.GENERATED))
            .isInstanceOf(SchemaValidationException.class);
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void updateSchemaThrowsWhenMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        when(schemaRepository.findById("missing")).thenReturn(Optional.empty());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.updateSchema("missing", schemaJson("contracts", 1, "Contract"), SchemaSourceType.GENERATED))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing");
    }

    @Test
    void updateSchemaRejectsIdentityChangeWithoutSaving() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        String updatedJson = schemaJson("contracts-renamed", 1, "Contract");
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(schemaRepository.existsActiveKnowledgeBaseReference("schema-01")).thenReturn(false);
        when(schemaParser.parse(updatedJson)).thenReturn(schemaDocument("contracts-renamed", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.updateSchema("schema-01", updatedJson, SchemaSourceType.GENERATED))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Schema identity is immutable");
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void updateSchemaRejectsActiveSchemaBeforeParsing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(schemaRepository.existsActiveKnowledgeBaseReference("schema-01")).thenReturn(true);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.updateSchema("schema-01", schemaJson("contracts", 1, "Contract"), SchemaSourceType.GENERATED))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Cannot update active schema: schema-01");
        verify(schemaParser, never()).parse(any());
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void deleteSchemaDetachesRelationshipsAndDeletesInactiveSchema() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        SchemaDefinitionNode schema = schemaNode("schema-01", "contracts", 1);
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schema));
        when(schemaRepository.existsActiveKnowledgeBaseReference("schema-01")).thenReturn(false);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        service.deleteSchema("schema-01");

        verify(schemaRepository).detachKnowledgeBaseAssociations("schema-01");
        verify(schemaRepository).delete(schema);
    }

    @Test
    void deleteSchemaThrowsWhenMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        when(schemaRepository.findById("missing")).thenReturn(Optional.empty());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.deleteSchema("missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing");
    }

    @Test
    void deleteSchemaRejectsActiveSchemaWithoutDetaching() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);

        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(schemaRepository.existsActiveKnowledgeBaseReference("schema-01")).thenReturn(true);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            knowledgeBaseRepository,
            neo4jClient
        );

        assertThatThrownBy(() -> service.deleteSchema("schema-01"))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Cannot delete active schema: schema-01");
        verify(schemaRepository, never()).detachKnowledgeBaseAssociations(any());
        verify(schemaRepository, never()).delete(any());
    }

    private SchemaDefinitionNode schemaNode(String id, String name, int version) {
        SchemaDefinitionNode node = new SchemaDefinitionNode();
        node.setId(id);
        node.setName(name);
        node.setVersion(version);
        node.setSourceType(SchemaSourceType.PREDEFINED);
        node.setFormat(SchemaFormat.JSON);
        node.setContent(schemaJson(name, version, "Contract"));
        node.setContentHash("old-hash");
        return node;
    }

    private SchemaDocument schemaDocument(String name, int version) {
        return new SchemaDocument(name, version, null, List.of(), List.of(), List.of(), List.of());
    }

    private String schemaJson(String name, int version, String label) {
        return """
            {
              "name": "%s",
              "version": %d,
              "nodes": [
                {"label": "%s", "key": "id", "properties": [{"name": "id", "type": "string"}]}
              ],
              "relationships": []
            }
            """.formatted(name, version, label);
    }
}
