package io.github.vfedoriv.graphrag.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HybridSearchRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresNonBlankQuery() {
        Set<ConstraintViolation<HybridSearchRequest>> violations =
            validator.validate(new HybridSearchRequest("   ", 10, 1, true));

        assertThat(violations).anySatisfy(violation ->
            assertThat(violation.getPropertyPath().toString()).isEqualTo("query"));
    }

    @Test
    void rejectsInvalidOptionalBounds() {
        Set<ConstraintViolation<HybridSearchRequest>> violations =
            validator.validate(new HybridSearchRequest("contracts", 0, -1, true));

        assertThat(violations)
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("topK", "graphDepth");
    }
}
