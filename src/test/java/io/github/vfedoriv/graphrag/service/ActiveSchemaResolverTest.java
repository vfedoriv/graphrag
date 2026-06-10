package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveSchemaResolverTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private SchemaDefinitionRepository schemaDefinitionRepository;

    @Test
    void resolvesActiveSchemaContext() {
        KnowledgeBaseNode kb = knowledgeBase("kb-1", "schema-1");
        SchemaDefinitionNode schemaDefinition = schemaDefinition("schema-1");
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(kb));
        when(schemaDefinitionRepository.findById("schema-1")).thenReturn(Optional.of(schemaDefinition));

        ActiveSchemaContext context = resolver().resolve("kb-1");

        assertThat(context.knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(context.schemaDefinitionId()).isEqualTo("schema-1");
        assertThat(context.schemaDefinition()).isSameAs(schemaDefinition);
        assertThat(context.schema().name()).isEqualTo("contracts");
        assertThat(context.schema().version()).isEqualTo(1);
    }

    @Test
    void failsWhenKnowledgeBaseDoesNotExist() {
        when(knowledgeBaseRepository.findById("missing-kb")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve("missing-kb"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
    }

    @Test
    void failsWhenKnowledgeBaseHasNoActiveSchema() {
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase("kb-1", null)));

        assertThatThrownBy(() -> resolver().resolve("kb-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No active schema for knowledge base: kb-1");
    }

    @Test
    void failsWhenKnowledgeBaseHasBlankActiveSchema() {
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase("kb-1", " ")));

        assertThatThrownBy(() -> resolver().resolve("kb-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No active schema for knowledge base: kb-1");
    }

    @Test
    void failsWhenActiveSchemaDefinitionDoesNotExist() {
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase("kb-1", "missing-schema")));
        when(schemaDefinitionRepository.findById("missing-schema")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve("kb-1"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing-schema");
    }

    private ActiveSchemaResolver resolver() {
        return new ActiveSchemaResolver(knowledgeBaseRepository, schemaDefinitionRepository, new SchemaParser());
    }

    private KnowledgeBaseNode knowledgeBase(String id, String activeSchemaId) {
        KnowledgeBaseNode kb = new KnowledgeBaseNode();
        kb.setId(id);
        kb.setActiveSchemaId(activeSchemaId);
        return kb;
    }

    private SchemaDefinitionNode schemaDefinition(String id) {
        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId(id);
        schema.setName("contracts");
        schema.setContent("""
            {
              "name": "contracts",
              "version": 1,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                }
              ],
              "relationships": []
            }
            """);
        return schema;
    }
}
