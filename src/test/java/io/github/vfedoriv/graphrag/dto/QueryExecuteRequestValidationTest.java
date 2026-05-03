package io.github.vfedoriv.graphrag.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class QueryExecuteRequestValidationTest {

    @Test
    void requiresNonBlankCypher() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new QueryExecuteRequest("   ", null));
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("cypher");
    }
}
