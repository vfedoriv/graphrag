package io.github.vfedoriv.graphrag.documents.domain.extraction;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphExtractionSupportTest {

    @Test
    void shouldRepairNodeKeyFromName() {
        SchemaDocument.NodeDefinition nodeDefinition = new SchemaDocument.NodeDefinition(
            "Party", null, List.of("partyId"), List.of(new SchemaDocument.PropertyDefinition("partyId", "string", false))
        );
        GraphExtractionResult.ExtractedNode node = new GraphExtractionResult.ExtractedNode(
            "Party", Map.of("partyId", "", "name", "Acme"), 0.9d
        );

        GraphExtractionResult.ExtractedNode normalized = GraphExtractionSupport.normalizeNode(node, nodeDefinition);

        assertThat(normalized.properties()).containsEntry("partyId", "Acme");
    }

    @Test
    void shouldNotRepairNodeWhenAllFallbackValuesAreBlank() {
        SchemaDocument.NodeDefinition nodeDefinition = new SchemaDocument.NodeDefinition(
            "Project", null, List.of("externalId"), List.of(new SchemaDocument.PropertyDefinition("externalId", "string", false))
        );
        GraphExtractionResult.ExtractedNode node = new GraphExtractionResult.ExtractedNode(
            "Project", Map.of("externalId", "", "name", " "), 0.5d
        );

        GraphExtractionResult.ExtractedNode normalized = GraphExtractionSupport.normalizeNode(node, nodeDefinition);

        assertThat(normalized.properties()).containsEntry("externalId", "");
    }

    @Test
    void shouldDropInvalidRelationshipTriple() {
        SchemaDocument schema = new SchemaDocument(
            "contracts", 1, null,
            List.of(
                new SchemaDocument.NodeDefinition("Contract", null, List.of("contractId"), List.of()),
                new SchemaDocument.NodeDefinition("Party", null, List.of("name"), List.of())
            ),
            List.of(new SchemaDocument.RelationshipDefinition("HAS_PARTY", "Contract", "Party", null, List.of())),
            List.of(),
            List.of()
        );
        GraphExtractionSupport.SchemaIndex index = GraphExtractionSupport.buildSchemaIndex(schema);
        GraphExtractionResult.ExtractedRelationship relationship = new GraphExtractionResult.ExtractedRelationship(
            "HAS_WEBSITE", "Contract", Map.of("contractId", "C-1"), "Party", Map.of("name", "Acme"), Map.of(), 1.0d
        );
        List<GraphExtractionResult.ExtractedNode> keptNodes = List.of(
            new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 1.0d),
            new GraphExtractionResult.ExtractedNode("Party", Map.of("name", "Acme"), 1.0d)
        );

        String dropReason = GraphExtractionSupport.relationshipDropReason(relationship, index, keptNodes);

        assertThat(dropReason).isEqualTo("invalid_relationship_triple");
    }

    @Test
    void shouldDropWhenEndpointLabelIsUnknown() {
        SchemaDocument schema = new SchemaDocument(
            "contracts", 1, null,
            List.of(new SchemaDocument.NodeDefinition("Contract", null, List.of("contractId"), List.of())),
            List.of(), List.of(), List.of()
        );
        GraphExtractionSupport.SchemaIndex index = GraphExtractionSupport.buildSchemaIndex(schema);
        GraphExtractionResult.ExtractedRelationship relationship = new GraphExtractionResult.ExtractedRelationship(
            "HAS_PARTY", "Contract", Map.of("contractId", "C-1"), "Party", Map.of("name", "Acme"), Map.of(), 1.0d
        );

        assertThat(GraphExtractionSupport.relationshipDropReason(relationship, index, List.of())).isEqualTo("unknown_endpoint_label");
    }

    @Test
    void shouldDropWhenEndpointDoesNotMatchKeptNode() {
        SchemaDocument schema = new SchemaDocument(
            "contracts", 1, null,
            List.of(
                new SchemaDocument.NodeDefinition("Contract", null, List.of("contractId"), List.of()),
                new SchemaDocument.NodeDefinition("Party", null, List.of("name"), List.of())
            ),
            List.of(new SchemaDocument.RelationshipDefinition("HAS_PARTY", "Contract", "Party", null, List.of())),
            List.of(), List.of()
        );
        GraphExtractionSupport.SchemaIndex index = GraphExtractionSupport.buildSchemaIndex(schema);
        GraphExtractionResult.ExtractedRelationship relationship = new GraphExtractionResult.ExtractedRelationship(
            "HAS_PARTY", "Contract", Map.of("contractId", "C-1"), "Party", Map.of("name", "Wrong"), Map.of(), 1.0d
        );
        List<GraphExtractionResult.ExtractedNode> keptNodes = List.of(
            new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 1.0d),
            new GraphExtractionResult.ExtractedNode("Party", Map.of("name", "Acme"), 1.0d)
        );

        assertThat(GraphExtractionSupport.relationshipDropReason(relationship, index, keptNodes)).isEqualTo("to_endpoint_not_kept");
    }

    @Test
    void shouldRepairEndpointWhenSingleNodeOfLabelExists() {
        SchemaDocument schema = new SchemaDocument(
            "contracts", 1, null,
            List.of(
                new SchemaDocument.NodeDefinition("Contract", null, List.of("contractId"), List.of()),
                new SchemaDocument.NodeDefinition("Party", null, List.of("name"), List.of())
            ),
            List.of(new SchemaDocument.RelationshipDefinition("HAS_PARTY", "Contract", "Party", null, List.of())),
            List.of(), List.of()
        );
        GraphExtractionSupport.SchemaIndex index = GraphExtractionSupport.buildSchemaIndex(schema);
        List<GraphExtractionResult.ExtractedNode> keptNodes = List.of(
            new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 1.0d),
            new GraphExtractionResult.ExtractedNode("Party", Map.of("name", "Acme"), 1.0d)
        );

        GraphExtractionResult.ExtractedRelationship normalized = GraphExtractionSupport.normalizeRelationship(
            new GraphExtractionResult.ExtractedRelationship(
                "HAS_PARTY", "Contract", Map.of(), "Party", Map.of("name", "Acme"), Map.of(), 1.0d
            ),
            index,
            keptNodes
        );

        assertThat(normalized.fromKey()).containsEntry("contractId", "C-1");
    }
}
