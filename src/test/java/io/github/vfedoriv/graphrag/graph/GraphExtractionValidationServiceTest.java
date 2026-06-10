package io.github.vfedoriv.graphrag.graph;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.error.GraphExtractionValidationException;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class GraphExtractionValidationServiceTest {

    private final GraphExtractionValidationService validationService =
        new GraphExtractionValidationService(
            new AppProperties(
                new AppProperties.Neo4j("neo4j"),
                new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
                new AppProperties.Storage(Path.of("var/documents")),
                new AppProperties.Chunking(800, 80, 4000),
                new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
                new AppProperties.Extraction(40, 80, 2)
            )
        );

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> validationService.validate(null, schema()))
            .isInstanceOf(GraphExtractionValidationException.class)
            .hasMessageContaining("must not be null");
    }

    @Test
    void rejectsPayloadAboveConfiguredNodeLimit() {
        GraphExtractionResult result = new GraphExtractionResult(
            java.util.stream.IntStream.range(0, 41)
                .mapToObj(index -> new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-" + index), 0.9))
                .toList(),
            List.of()
        );

        assertThatThrownBy(() -> validationService.validate(result, schema()))
            .isInstanceOf(GraphExtractionValidationException.class)
            .hasMessageContaining("Too many extracted entities");
    }

    @Test
    void skipsUnknownLabelAndKeepsValidNodes(CapturedOutput output) {
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(
                new GraphExtractionResult.ExtractedNode("Unknown", Map.of("id", "1"), 0.9),
                new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 0.9)
            ),
            List.of()
        );

        GraphExtractionResult validated = validationService.validate(result, schema());

        assertThat(validated.nodes()).extracting(GraphExtractionResult.ExtractedNode::label).containsExactly("Contract");
        assertThat(output).contains("Dropped extracted node");
        assertThat(output).contains("reason=unknown_label");
    }

    @Test
    void acceptsValidPayload() {
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(
                new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 0.9),
                new GraphExtractionResult.ExtractedNode("Party", Map.of("name", "Acme"), 0.8)
            ),
            List.of(
                new GraphExtractionResult.ExtractedRelationship(
                    "HAS_PARTY",
                    "Contract",
                    Map.of("contractId", "C-1"),
                    "Party",
                    Map.of("name", "Acme"),
                    Map.of("role", "Supplier"),
                    0.7
                )
            )
        );
        assertThatCode(() -> validationService.validate(result, schema())).doesNotThrowAnyException();
        GraphExtractionResult validated = validationService.validate(result, schema());
        assertThat(validated.nodes()).hasSize(2);
        assertThat(validated.relationships()).hasSize(1);
    }

    @Test
    void dropsInvalidRelationshipTriplesAndKeepsValidOnes() {
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(
                new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 0.9),
                new GraphExtractionResult.ExtractedNode("Party", Map.of("name", "Acme"), 0.8),
                new GraphExtractionResult.ExtractedNode("Address", Map.of("id", "A-1"), 0.7)
            ),
            List.of(
                new GraphExtractionResult.ExtractedRelationship(
                    "HAS_PARTY",
                    "Contract",
                    Map.of("contractId", "C-1"),
                    "Party",
                    Map.of("name", "Acme"),
                    Map.of("role", "Supplier"),
                    0.7
                ),
                new GraphExtractionResult.ExtractedRelationship(
                    "HAS_WEBSITE",
                    "Contract",
                    Map.of("contractId", "C-1"),
                    "Address",
                    Map.of("id", "A-1"),
                    Map.of(),
                    0.6
                )
            )
        );

        GraphExtractionResult validated = validationService.validate(result, schema());
        assertThat(validated.relationships()).hasSize(1);
        assertThat(validated.relationships().getFirst().type()).isEqualTo("HAS_PARTY");
    }

    @Test
    void acceptsCompositeNodeKeyAndEndpointKeys() {
        SchemaDocument schema = new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition(
                    "Person",
                    "",
                    List.of("fullName", "birthDate"),
                    List.of(
                        new SchemaDocument.PropertyDefinition("fullName", "string", false),
                        new SchemaDocument.PropertyDefinition("birthDate", "date", false)
                    )
                )
            ),
            List.of(),
            List.of(),
            List.of()
        );
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode(
                "Person",
                Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                0.9
            )),
            List.of()
        );

        assertThatCode(() -> validationService.validate(result, schema)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRelationshipWithCompleteCompositeEndpoints() {
        SchemaDocument schema = new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition(
                    "Person",
                    "",
                    List.of("fullName", "birthDate"),
                    List.of(
                        new SchemaDocument.PropertyDefinition("fullName", "string", false),
                        new SchemaDocument.PropertyDefinition("birthDate", "date", false)
                    )
                )
            ),
            List.of(new SchemaDocument.RelationshipDefinition("KNOWS", "Person", "Person", "", List.of())),
            List.of(),
            List.of()
        );
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode(
                "Person",
                Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                0.9
            )),
            List.of(new GraphExtractionResult.ExtractedRelationship(
                "KNOWS",
                "Person",
                Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                "Person",
                Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                Map.of(),
                0.7
            ))
        );

        assertThatCode(() -> validationService.validate(result, schema)).doesNotThrowAnyException();
    }

    @Test
    void repairsMissingNodeKeyComponentsWhenPreferredPropertyExists(CapturedOutput output) {
        SchemaDocument schema = new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition(
                    "Project",
                    "",
                    List.of("externalId"),
                    List.of(
                        new SchemaDocument.PropertyDefinition("externalId", "string", false),
                        new SchemaDocument.PropertyDefinition("name", "string", false)
                    )
                )
            ),
            List.of(),
            List.of(),
            List.of()
        );
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode(
                "Project",
                Map.of("name", "Apollo"),
                0.9
            )),
            List.of()
        );

        GraphExtractionResult validated = validationService.validate(result, schema);
        assertThat(validated.nodes()).hasSize(1);
        assertThat(validated.nodes().getFirst().properties()).containsEntry("externalId", "Apollo");
        assertThat(output).contains("Repaired extracted node key");
        assertThat(output).contains("reason=node_key_repaired");
    }

    @Test
    void skipsNodeWhenMissingKeyCannotBeRepaired(CapturedOutput output) {
        SchemaDocument schema = new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(new SchemaDocument.NodeDefinition(
                "Person",
                "",
                List.of("fullName", "birthDate"),
                List.of(
                    new SchemaDocument.PropertyDefinition("fullName", "string", false),
                    new SchemaDocument.PropertyDefinition("birthDate", "date", false)
                )
            )),
            List.of(),
            List.of(),
            List.of()
        );
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(
                new GraphExtractionResult.ExtractedNode(
                    "Person",
                    Map.of("fullName", "Ada Lovelace"),
                    0.9
                ),
                new GraphExtractionResult.ExtractedNode(
                    "Person",
                    Map.of("fullName", "Grace Hopper", "birthDate", "1906-12-09"),
                    0.8
                )
            ),
            List.of()
        );

        GraphExtractionResult validated = validationService.validate(result, schema);

        assertThat(validated.nodes()).hasSize(1);
        assertThat(validated.nodes().getFirst().properties()).containsEntry("fullName", "Grace Hopper");
        assertThat(output).contains("Dropped extracted node");
        assertThat(output).contains("reason=incomplete_node_key");
    }

    @Test
    void fillsMissingCompositeEndpointComponentWhenSingleMatchingNodeExists(CapturedOutput output) {
        SchemaDocument schema = new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition(
                    "Person",
                    "",
                    List.of("fullName", "birthDate"),
                    List.of(
                        new SchemaDocument.PropertyDefinition("fullName", "string", false),
                        new SchemaDocument.PropertyDefinition("birthDate", "date", false)
                    )
                )
            ),
            List.of(new SchemaDocument.RelationshipDefinition("KNOWS", "Person", "Person", "", List.of())),
            List.of(),
            List.of()
        );
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode(
                "Person",
                Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                0.9
            )),
            List.of(new GraphExtractionResult.ExtractedRelationship(
                "KNOWS",
                "Person",
                Map.of("fullName", "Ada Lovelace"),
                "Person",
                Map.of("fullName", "Ada Lovelace"),
                Map.of(),
                0.7
            ))
        );

        GraphExtractionResult validated = validationService.validate(result, schema);
        assertThat(validated.relationships()).hasSize(1);
        assertThat(validated.relationships().getFirst().fromKey()).containsEntry("birthDate", "1815-12-10");
        assertThat(validated.relationships().getFirst().toKey()).containsEntry("birthDate", "1815-12-10");
        assertThat(output).contains("Repaired relationship endpoint key");
        assertThat(output).contains("reason=endpoint_key_repaired");
    }

    @Test
    void skipsPartialCompositeRelationshipEndpointKeyAndKeepsValidRelationship(CapturedOutput output) {
        SchemaDocument schema = new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition(
                    "Person",
                    "",
                    List.of("fullName", "birthDate"),
                    List.of(
                        new SchemaDocument.PropertyDefinition("fullName", "string", false),
                        new SchemaDocument.PropertyDefinition("birthDate", "date", false)
                    )
                )
            ),
            List.of(new SchemaDocument.RelationshipDefinition("KNOWS", "Person", "Person", "", List.of())),
            List.of(),
            List.of()
        );
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(
                new GraphExtractionResult.ExtractedNode(
                    "Person",
                    Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                    0.9
                ),
                new GraphExtractionResult.ExtractedNode(
                    "Person",
                    Map.of("fullName", "Grace Hopper", "birthDate", "1906-12-09"),
                    0.9
                )
            ),
            List.of(
                new GraphExtractionResult.ExtractedRelationship(
                    "KNOWS",
                    "Person",
                    Map.of("fullName", "Ada Lovelace"),
                    "Person",
                    Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                    Map.of(),
                    0.7
                ),
                new GraphExtractionResult.ExtractedRelationship(
                    "KNOWS",
                    "Person",
                    Map.of("fullName", "Ada Lovelace", "birthDate", "1815-12-10"),
                    "Person",
                    Map.of("fullName", "Grace Hopper", "birthDate", "1906-12-09"),
                    Map.of(),
                    0.8
                )
            )
        );

        GraphExtractionResult validated = validationService.validate(result, schema);

        assertThat(validated.relationships()).hasSize(1);
        assertThat(validated.relationships().getFirst().toKey()).containsEntry("fullName", "Grace Hopper");
        assertThat(output).contains("Dropped extracted relationship");
        assertThat(output).contains("reason=incomplete_from_endpoint_key");
    }

    @Test
    void skipsRelationshipWhenEndpointIdentityDoesNotMatchKeptNode(CapturedOutput output) {
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(
                new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-1"), 0.9),
                new GraphExtractionResult.ExtractedNode("Party", Map.of("name", "Acme"), 0.8)
            ),
            List.of(
                new GraphExtractionResult.ExtractedRelationship(
                    "HAS_PARTY",
                    "Contract",
                    Map.of("contractId", "C-1"),
                    "Party",
                    Map.of("name", "Missing Party"),
                    Map.of("role", "Supplier"),
                    0.7
                )
            )
        );

        GraphExtractionResult validated = validationService.validate(result, schema());

        assertThat(validated.nodes()).hasSize(2);
        assertThat(validated.relationships()).isEmpty();
        assertThat(output).contains("Dropped extracted relationship");
        assertThat(output).contains("reason=to_endpoint_not_kept");
    }

    private SchemaDocument schema() {
        return new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition("Contract", "", List.of("contractId"), List.of()),
                new SchemaDocument.NodeDefinition("Party", "", List.of("name"), List.of()),
                new SchemaDocument.NodeDefinition("Address", "", List.of("id"), List.of())
            ),
            List.of(new SchemaDocument.RelationshipDefinition("HAS_PARTY", "Contract", "Party", "", List.of())),
            List.of(),
            List.of()
        );
    }
}
