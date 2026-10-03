package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.schemas.generation.api.model.GenerateSchemaExampleRequest;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GenerateSchemaExampleRequestValidationTest {

    @Test
    void requiresNonBlankText() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<GenerateSchemaExampleRequest>> violations =
            validator.validate(new GenerateSchemaExampleRequest("   ", "hint"));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("text");
    }
}
