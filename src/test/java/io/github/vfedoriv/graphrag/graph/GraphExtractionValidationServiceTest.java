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

class GraphExtractionValidationServiceTest {

    private final GraphExtractionValidationService validationService =
        new GraphExtractionValidationService(
            new AppProperties(
                new AppProperties.Neo4j("neo4j"),
                new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
                new AppProperties.Storage(Path.of("var/documents")),
                new AppProperties.Chunking(800, 80, 4000),
                new AppProperties.Query(200, 15, true, List.of("CREATE")),
                new AppProperties.Extraction(40, 80, 2)
            )
        );

    @Test
    void rejectsUnknownLabel() {
        GraphExtractionResult result = new GraphExtractionResult(
            List.of(new GraphExtractionResult.ExtractedNode("Unknown", Map.of("id", "1"), 0.9)),
            List.of()
        );
        assertThatThrownBy(() -> validationService.validate(result, schema()))
            .isInstanceOf(GraphExtractionValidationException.class)
            .hasMessageContaining("Unknown node label");
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

    private SchemaDocument schema() {
        return new SchemaDocument(
            "contracts",
            1,
            "test",
            List.of(
                new SchemaDocument.NodeDefinition("Contract", "", "contractId", List.of()),
                new SchemaDocument.NodeDefinition("Party", "", "name", List.of()),
                new SchemaDocument.NodeDefinition("Address", "", "id", List.of())
            ),
            List.of(new SchemaDocument.RelationshipDefinition("HAS_PARTY", "Contract", "Party", "", List.of())),
            List.of(),
            List.of()
        );
    }
}
