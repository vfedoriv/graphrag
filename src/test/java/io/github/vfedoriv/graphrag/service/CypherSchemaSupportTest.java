package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CypherSchemaSupportTest {

    @Test
    void shouldBuildAllowedLabelsIncludingInfraAndSchemaLabels() {
        SchemaDocument schema = new SchemaDocument(
            "contracts", 1, null,
            List.of(new SchemaDocument.NodeDefinition("Contract", null, List.of("id"), List.of())),
            List.of(), List.of(), List.of()
        );

        Set<String> labels = CypherSchemaSupport.allowedLabels(schema, Set.of("KnowledgeBase"));

        assertThat(labels).containsExactlyInAnyOrder("KnowledgeBase", "Contract");
    }

    @Test
    void shouldBuildAllowedRelationshipTypesAndProperties() {
        SchemaDocument schema = new SchemaDocument(
            "contracts", 1, null,
            List.of(new SchemaDocument.NodeDefinition(
                "Contract", null, List.of("id"), List.of(new SchemaDocument.PropertyDefinition("contractId", "string", false))
            )),
            List.of(new SchemaDocument.RelationshipDefinition(
                "HAS_PARTY", "Contract", "Party", null,
                List.of(new SchemaDocument.PropertyDefinition("role", "string", false))
            )),
            List.of(), List.of()
        );

        assertThat(CypherSchemaSupport.allowedRelationshipTypes(schema)).containsExactly("HAS_PARTY");
        assertThat(CypherSchemaSupport.allowedProperties(schema))
            .contains("contractId", "role", "id", "sourceDocumentId", "sourceChunkIds", "schemaId", "extractionRunId", "confidence", "createdAt");
    }
}
