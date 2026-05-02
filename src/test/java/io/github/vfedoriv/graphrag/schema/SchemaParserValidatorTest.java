package io.github.vfedoriv.graphrag.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaParserValidatorTest {

    private final SchemaParser parser = new SchemaParser();
    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void parsesAndValidatesSchema() {
        String yaml = """
            name: sample
            version: 1
            nodes:
              - label: Company
                key: id
            relationships:
              - type: OWNS
                from: Company
                to: Company
            """;

        SchemaDocument doc = parser.parse(yaml);
        List<String> errors = validator.validate(doc);

        assertThat(doc.name()).isEqualTo("sample");
        assertThat(errors).isEmpty();
    }

    @Test
    void reportsValidationErrors() {
        String yaml = """
            name: sample
            version: 0
            nodes:
              - label: Company
                key: id
              - label: Company
                key: id2
            relationships:
              - type: OWNS
                from: Unknown
                to: Company
            vectorIndexes:
              - name: x
                label: DocumentChunk
                property: embedding
                dimensions: 0
                similarity: weird
            """;

        List<String> errors = validator.validate(parser.parse(yaml));
        assertThat(errors).anyMatch(it -> it.contains("version"));
        assertThat(errors).anyMatch(it -> it.contains("duplicate label"));
        assertThat(errors).anyMatch(it -> it.contains("unknown node label"));
        assertThat(errors).anyMatch(it -> it.contains("dimensions"));
        assertThat(errors).anyMatch(it -> it.contains("unsupported value"));
    }
}
