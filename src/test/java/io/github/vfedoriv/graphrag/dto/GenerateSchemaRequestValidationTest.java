package io.github.vfedoriv.graphrag.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class GenerateSchemaRequestValidationTest {

    @Test
    void requiresNonBlankExample() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new GenerateSchemaRequest("schema", 1, "desc", "text", "   "));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("example");
    }
}
