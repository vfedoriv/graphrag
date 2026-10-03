package io.github.vfedoriv.graphrag.schemas.registry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaFormat;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaStatus;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StoredSchemaSnapshotsFacadeTest {
    private final SchemaDefinitionRepository definitions = mock(SchemaDefinitionRepository.class);
    private final SchemaRegistryService registry = mock(SchemaRegistryService.class);
    private final SchemaParser parser = mock(SchemaParser.class);
    private final StoredSchemaSnapshotsFacade facade = new StoredSchemaSnapshotsFacade(definitions, registry, parser);

    @Test
    void findByIdPreservesExactStoredContentAndHash() {
        String content = " { \"name\": \"stored-schema\", \"version\": 1 } ";
        String contentHash = "sha256-of-exact-stored-content";
        SchemaDefinitionNode definition = definition("schema-1", content, contentHash, SchemaStatus.INACTIVE);
        when(definitions.findById("schema-1")).thenReturn(Optional.of(definition));
        when(parser.parse(content)).thenReturn(emptySchema());

        SchemaSnapshot snapshot = facade.findById("schema-1").orElseThrow();

        assertThat(snapshot.schemaDefinitionId()).isEqualTo("schema-1");
        assertThat(snapshot.content()).isEqualTo(content);
        assertThat(snapshot.contentHash()).isEqualTo(contentHash);
        assertThat(snapshot.knowledgeBaseId()).isNull();
        assertThat(snapshot.schema()).isNull();
        verifyNoInteractions(parser);
    }

    @Test
    void findByIdReturnsEmptyWhenSchemaIsMissing() {
        when(definitions.findById("missing")).thenReturn(Optional.empty());

        assertThat(facade.findById("missing")).isEmpty();
    }

    @Test
    void associatedUsesRegistryScopedStatusAndKnowledgeBaseId() {
        String content = "{\"name\":\"scoped-schema\",\"version\":1}";
        SchemaDefinitionNode scopedDefinition = definition(
            "schema-2", content, "scoped-hash", SchemaStatus.ACTIVE);
        when(registry.listSchemasByKnowledgeBase("kb-1")).thenReturn(List.of(scopedDefinition));
        when(parser.parse(content)).thenReturn(emptySchema());

        List<SchemaSnapshot> snapshots = facade.associated("kb-1");

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(snapshots.get(0).status()).isEqualTo(SchemaStatus.ACTIVE);
        verify(registry).listSchemasByKnowledgeBase("kb-1");
        assertThat(snapshots.get(0).schema()).isNull();
        verifyNoInteractions(parser);
    }

    @Test
    void parsedSchemaValuesAreDefensivelyCopiedAndImmutable() {
        List<String> nodeKey = new ArrayList<>(List.of("id"));
        List<SchemaDocument.PropertyDefinition> nodeProperties = new ArrayList<>(List.of(
            new SchemaDocument.PropertyDefinition("label", "string", true)));
        List<SchemaDocument.NodeDefinition> nodes = new ArrayList<>(List.of(
            new SchemaDocument.NodeDefinition("Thing", "A thing", nodeKey, nodeProperties)));
        List<SchemaDocument.PropertyDefinition> relationshipProperties = new ArrayList<>(List.of(
            new SchemaDocument.PropertyDefinition("since", "date", false)));
        List<SchemaDocument.RelationshipDefinition> relationships = new ArrayList<>(List.of(
            new SchemaDocument.RelationshipDefinition("RELATED_TO", "Thing", "Thing", null,
                relationshipProperties)));
        List<String> indexProperties = new ArrayList<>(List.of("id"));
        List<SchemaDocument.IndexDefinition> indexes = new ArrayList<>(List.of(
            new SchemaDocument.IndexDefinition("Thing", indexProperties, true)));
        List<SchemaDocument.VectorIndexDefinition> vectorIndexes = new ArrayList<>(List.of(
            new SchemaDocument.VectorIndexDefinition("thing-embedding", "Thing", "embedding", 8, "cosine")));
        SchemaDocument parsed = new SchemaDocument(
            "immutable-schema", 1, "description", nodes, relationships, indexes, vectorIndexes);
        String content = "stored schema";
        when(definitions.findById("schema-3"))
            .thenReturn(Optional.of(definition("schema-3", content, "schema-hash", SchemaStatus.INACTIVE)));
        when(parser.parse(content)).thenReturn(parsed);

        SchemaSnapshot snapshot = facade.findParsedById("schema-3").orElseThrow();

        nodeKey.clear();
        nodeProperties.clear();
        nodes.clear();
        relationshipProperties.clear();
        relationships.clear();
        indexProperties.clear();
        indexes.clear();
        vectorIndexes.clear();

        SchemaDocument snapshotSchema = snapshot.schema();
        assertThat(snapshotSchema.nodes()).hasSize(1);
        assertThat(snapshotSchema.nodes().get(0).key()).containsExactly("id");
        assertThat(snapshotSchema.nodes().get(0).properties()).containsExactly(
            new SchemaDocument.PropertyDefinition("label", "string", true));
        assertThat(snapshotSchema.relationships().get(0).properties()).containsExactly(
            new SchemaDocument.PropertyDefinition("since", "date", false));
        assertThat(snapshotSchema.indexes().get(0).properties()).containsExactly("id");
        assertThat(snapshotSchema.vectorIndexes()).containsExactly(
            new SchemaDocument.VectorIndexDefinition("thing-embedding", "Thing", "embedding", 8, "cosine"));

        assertThatThrownBy(() -> snapshotSchema.nodes().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshotSchema.relationships().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshotSchema.indexes().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshotSchema.nodes().get(0).key().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshotSchema.nodes().get(0).properties().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshotSchema.relationships().get(0).properties().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshotSchema.indexes().get(0).properties().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshotSchema.vectorIndexes().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private SchemaDefinitionNode definition(String id, String content, String contentHash, SchemaStatus status) {
        SchemaDefinitionNode definition = new SchemaDefinitionNode();
        definition.setId(id);
        definition.setName("stored-schema");
        definition.setVersion(1);
        definition.setSourceType(SchemaSourceType.GENERATED);
        definition.setFormat(SchemaFormat.JSON);
        definition.setContent(content);
        definition.setContentHash(contentHash);
        definition.setStatus(status);
        return definition;
    }

    private SchemaDocument emptySchema() {
        return new SchemaDocument("stored-schema", 1, null, List.of(), List.of(), List.of(), List.of());
    }
}
