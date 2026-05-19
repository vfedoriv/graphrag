package io.github.vfedoriv.graphrag.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaParserValidatorTest {

    private final SchemaParser parser = new SchemaParser();
    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void parsesAndValidatesSchema() {
        String json = """
            {
              "name": "sample",
              "version": 1,
              "nodes": [
                {
                  "label": "Company",
                  "key": "id",
                  "properties": [{"name": "id", "type": "string"}]
                }
              ],
              "relationships": [
                {"type": "OWNS", "from": "Company", "to": "Company"}
              ]
            }
            """;

        SchemaDocument doc = parser.parse(json);
        List<String> errors = validator.validate(doc);

        assertThat(doc.name()).isEqualTo("sample");
        assertThat(errors).isEmpty();
    }

    @Test
    void reportsValidationErrors() {
        String json = """
            {
              "name": "sample",
              "version": 0,
              "nodes": [
                {"label": "Company", "key": "id"},
                {"label": "Company", "key": "id2"}
              ],
              "relationships": [
                {"type": "OWNS", "from": "Unknown", "to": "Company"}
              ],
              "vectorIndexes": [
                {"name": "x", "label": "DocumentChunk", "property": "embedding", "dimensions": 0, "similarity": "weird"}
              ]
            }
            """;

        List<String> errors = validator.validate(parser.parse(json));
        assertThat(errors).anyMatch(it -> it.contains("version"));
        assertThat(errors).anyMatch(it -> it.contains("duplicate label"));
        assertThat(errors).anyMatch(it -> it.contains("unknown node label"));
        assertThat(errors).anyMatch(it -> it.contains("dimensions"));
        assertThat(errors).anyMatch(it -> it.contains("unsupported value"));
    }

    @Test
    void validatesNodeKeyDeclaredInProperties() {
        String validJson = """
            {
              "name": "sample",
              "version": 1,
              "nodes": [
                {"label": "Company", "key": "id", "properties": [{"name": "id", "type": "string"}]}
              ],
              "relationships": []
            }
            """;
        String missingKeyPropertyJson = """
            {
              "name": "sample",
              "version": 1,
              "nodes": [
                {"label": "Company", "key": "id", "properties": [{"name": "name", "type": "string"}]}
              ],
              "relationships": []
            }
            """;
        String emptyPropertiesJson = """
            {
              "name": "sample",
              "version": 1,
              "nodes": [
                {"label": "Company", "key": "id", "properties": []}
              ],
              "relationships": []
            }
            """;

        List<String> validErrors = validator.validate(parser.parse(validJson));
        List<String> missingKeyErrors = validator.validate(parser.parse(missingKeyPropertyJson));
        List<String> emptyPropertyErrors = validator.validate(parser.parse(emptyPropertiesJson));

        assertThat(validErrors).isEmpty();
        assertThat(missingKeyErrors).anyMatch(it -> it.contains("missing from declared properties"));
        assertThat(emptyPropertyErrors).anyMatch(it -> it.contains("must be declared as a property"));
    }

    @Test
    void validatesCompositeNodeKeyDeclaredInProperties() {
        String validCompositeKeyJson = """
            {
              "name": "sample",
              "version": 1,
              "nodes": [
                {"label": "Person", "key": ["fullName", "birthDate"], "properties": [
                  {"name": "fullName", "type": "string"},
                  {"name": "birthDate", "type": "date"}
                ]}
              ],
              "relationships": []
            }
            """;
        String missingCompositeKeyPartJson = """
            {
              "name": "sample",
              "version": 1,
              "nodes": [
                {"label": "Person", "key": ["fullName", "birthDate"], "properties": [
                  {"name": "fullName", "type": "string"}
                ]}
              ],
              "relationships": []
            }
            """;

        List<String> validErrors = validator.validate(parser.parse(validCompositeKeyJson));
        List<String> missingKeyErrors = validator.validate(parser.parse(missingCompositeKeyPartJson));

        assertThat(validErrors).isEmpty();
        assertThat(missingKeyErrors).anyMatch(it -> it.contains("birthDate") && it.contains("missing from declared properties"));
    }
}
