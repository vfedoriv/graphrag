package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.registry.application.ActiveSchemaResolver;
import io.github.vfedoriv.graphrag.schemas.registry.application.ActiveSchemaContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.schemas.registry.ports.KnowledgeBaseAdmission;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaKnowledgeBase;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaParser;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveSchemaResolverTest {

    @Mock
    private KnowledgeBaseAdmission knowledgeBaseAdmission;
    @Mock
    private SchemaDefinitionRepository schemaDefinitionRepository;

    @Test
    void resolvesActiveSchemaContext() {
        KnowledgeBaseNode kb = knowledgeBase("kb-1", "schema-1");
        SchemaDefinitionNode schemaDefinition = schemaDefinition("schema-1");
        when(knowledgeBaseAdmission.requireManaged("kb-1")).thenReturn(new SchemaKnowledgeBase("kb-1", kb.getActiveSchemaId()));
        when(schemaDefinitionRepository.findById("schema-1")).thenReturn(Optional.of(schemaDefinition));

        ActiveSchemaContext context = resolver().resolve("kb-1");

        assertThat(context.knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(context.schemaDefinitionId()).isEqualTo("schema-1");
        assertThat(context.schemaDefinition().getContent()).isEqualTo(schemaDefinition.getContent());
        assertThat(context.schema().name()).isEqualTo("contracts");
        assertThat(context.schema().version()).isEqualTo(1);
    }

    @Test
    void failsWhenKnowledgeBaseDoesNotExist() {
        when(knowledgeBaseAdmission.requireManaged("missing-kb"))
            .thenThrow(new NotFoundException("Knowledge base not found: missing-kb"));

        assertThatThrownBy(() -> resolver().resolve("missing-kb"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
    }

    @Test
    void failsWhenKnowledgeBaseHasNoActiveSchema() {
        when(knowledgeBaseAdmission.requireManaged("kb-1"))
            .thenReturn(new SchemaKnowledgeBase("kb-1", null));

        assertThatThrownBy(() -> resolver().resolve("kb-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No active schema for knowledge base: kb-1");
    }

    @Test
    void failsWhenKnowledgeBaseHasBlankActiveSchema() {
        when(knowledgeBaseAdmission.requireManaged("kb-1"))
            .thenReturn(new SchemaKnowledgeBase("kb-1", " "));

        assertThatThrownBy(() -> resolver().resolve("kb-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No active schema for knowledge base: kb-1");
    }

    @Test
    void failsWhenActiveSchemaDefinitionDoesNotExist() {
        when(knowledgeBaseAdmission.requireManaged("kb-1"))
            .thenReturn(new SchemaKnowledgeBase("kb-1", "missing-schema"));
        when(schemaDefinitionRepository.findById("missing-schema")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve("kb-1"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing-schema");
    }

    @Test
    void snapshotUsesExactStoredContentAndHash() {
        SchemaDefinitionNode definition = schemaDefinition("schema-1");
        definition.setContentHash("stored-hash");
        when(knowledgeBaseAdmission.requireManaged("kb-1"))
            .thenReturn(new SchemaKnowledgeBase("kb-1", "schema-1"));
        when(schemaDefinitionRepository.findById("schema-1"))
            .thenReturn(Optional.of(definition));

        SchemaSnapshot snapshot = resolver().resolveExpectedSnapshot("kb-1", "schema-1", "stored-hash");

        assertThat(snapshot.content()).isEqualTo(definition.getContent());
        assertThat(snapshot.contentHash()).isEqualTo("stored-hash");
        assertThat(snapshot.schemaDefinitionId()).isEqualTo("schema-1");
        assertThat(snapshot.schema().name()).isEqualTo("contracts");
    }

    @Test
    void snapshotRejectsChangedActiveAssociation() {
        when(knowledgeBaseAdmission.requireManaged("kb-1"))
            .thenReturn(new SchemaKnowledgeBase("kb-1", "schema-2"));

        assertThatThrownBy(() -> resolver().resolveExpectedSnapshot("kb-1", "schema-1", "hash"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Active schema no longer matches immutable processing target");
    }

    @Test
    void snapshotRejectsChangedContentForSameIdentity() {
        SchemaDefinitionNode definition = schemaDefinition("schema-1");
        definition.setContentHash("new-hash");
        when(knowledgeBaseAdmission.requireManaged("kb-1"))
            .thenReturn(new SchemaKnowledgeBase("kb-1", "schema-1"));
        when(schemaDefinitionRepository.findById("schema-1"))
            .thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> resolver().resolveExpectedSnapshot("kb-1", "schema-1", "old-hash"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Schema content no longer matches immutable processing target");
    }

    @Test
    void expectedSnapshotPreservesMissingKnowledgeBaseError() {
        when(knowledgeBaseAdmission.requireManaged("missing-kb"))
            .thenThrow(new NotFoundException("Knowledge base not found: missing-kb"));

        assertThatThrownBy(() -> resolver().resolveExpectedSnapshot("missing-kb", "schema-1", "hash"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
    }

    @Test
    void expectedSnapshotPreservesMissingDefinitionError() {
        when(knowledgeBaseAdmission.requireManaged("kb-1"))
            .thenReturn(new SchemaKnowledgeBase("kb-1", "schema-1"));
        when(schemaDefinitionRepository.findById("schema-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolveExpectedSnapshot("kb-1", "schema-1", "hash"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: schema-1");
    }

    private ActiveSchemaResolver resolver() {
        return new ActiveSchemaResolver(knowledgeBaseAdmission, schemaDefinitionRepository, new SchemaParser());
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
