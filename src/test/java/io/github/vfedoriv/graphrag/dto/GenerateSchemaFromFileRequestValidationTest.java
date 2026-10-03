package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.schemas.generation.api.model.GenerateSchemaFromFileRequest;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GenerateSchemaFromFileRequestValidationTest {

    private final jakarta.validation.Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesNullExample() {
        Set<ConstraintViolation<GenerateSchemaFromFileRequest>> violations =
            validator.validate(new GenerateSchemaFromFileRequest("schema", 1, "desc", null));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("example");
    }
}
