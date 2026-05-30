package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import io.github.vfedoriv.graphrag.schema.SchemaValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.neo4j.core.Neo4jClient;

class SchemaRegistryServiceTest {

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
}
