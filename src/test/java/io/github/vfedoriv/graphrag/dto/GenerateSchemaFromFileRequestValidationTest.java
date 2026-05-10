package io.github.vfedoriv.graphrag.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class GenerateSchemaFromFileRequestValidationTest {

    private final jakarta.validation.Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesNullExample() {
        var violations = validator.validate(new GenerateSchemaFromFileRequest("schema", 1, "desc", null));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("example");
    }
}
