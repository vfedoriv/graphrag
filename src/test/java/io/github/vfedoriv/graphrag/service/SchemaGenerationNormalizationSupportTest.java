package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.generation.domain.SchemaGenerationNormalizationSupport;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaGenerationNormalizationSupportTest {

    @Test
    void shouldSanitizeLabelsAndRelationsWithFallbacks() {
        assertThat(SchemaGenerationNormalizationSupport.sanitizeLabel("my-label", "Entity")).isEqualTo("My_label");
        assertThat(SchemaGenerationNormalizationSupport.sanitizeLabel(" ", "Entity")).isEqualTo("Entity");
        assertThat(SchemaGenerationNormalizationSupport.sanitizeRelation(" has party ")).isEqualTo("HAS_PARTY");
        assertThat(SchemaGenerationNormalizationSupport.sanitizeRelation("___")).isEqualTo("RELATED_TO");
    }

    @Test
    void shouldInferPropertyTypesAcrossCommonFormats() {
        assertThat(SchemaGenerationNormalizationSupport.inferPropertyType("true")).isEqualTo("boolean");
        assertThat(SchemaGenerationNormalizationSupport.inferPropertyType("-12")).isEqualTo("integer");
        assertThat(SchemaGenerationNormalizationSupport.inferPropertyType("3.14")).isEqualTo("number");
        assertThat(SchemaGenerationNormalizationSupport.inferPropertyType("2026-05-20")).isEqualTo("date");
        assertThat(SchemaGenerationNormalizationSupport.inferPropertyType("2026-05-20T12:30:45Z")).isEqualTo("datetime");
        assertThat(SchemaGenerationNormalizationSupport.inferPropertyType("abc")).isEqualTo("string");
    }

    @Test
    void shouldInferKeyCandidatesAndSafePropertyNames() {
        List<SchemaDocument.PropertyDefinition> properties = List.of(
            new SchemaDocument.PropertyDefinition("manufacturer", "string", false),
            new SchemaDocument.PropertyDefinition("modelCode", "string", false),
            new SchemaDocument.PropertyDefinition("name", "string", false)
        );

        assertThat(SchemaGenerationNormalizationSupport.inferKeyCandidates(properties)).containsExactly("modelCode");
        assertThat(SchemaGenerationNormalizationSupport.isSafePropertyName("good_name_1")).isTrue();
        assertThat(SchemaGenerationNormalizationSupport.isSafePropertyName("1bad")).isFalse();
        assertThat(SchemaGenerationNormalizationSupport.firstNonBlank(" ", "fallback")).isEqualTo("fallback");
    }
}
