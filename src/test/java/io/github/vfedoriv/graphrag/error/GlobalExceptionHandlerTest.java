package io.github.vfedoriv.graphrag.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.schema.SchemaValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void queryRejectedProblemIncludesValidationErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/query/execute");
        QueryRejectedException exception = new QueryRejectedException(List.of("DELETE is blocked", "Missing LIMIT"));

        ProblemDetail problem = handler.handleQueryRejected(exception, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Query validation failed");
        assertThat(problem.getDetail()).isEqualTo("Query validation failed");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/query/execute");
        assertThat(problem.getProperties()).containsEntry("errors", List.of("DELETE is blocked", "Missing LIMIT"));
    }

    @Test
    void mapsQueryDeadlineWithoutRawQueryDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/knowledge-bases/kb-1/queries/execute");

        ProblemDetail deadline = handler.handleQueryDeadlineExceeded(new QueryDeadlineExceededException(new RuntimeException("MATCH secret")), request);

        assertThat(deadline.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value());
        assertThat(deadline.getDetail()).doesNotContain("MATCH");
    }

    @Test
    void schemaValidationProblemIncludesErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/schemas");
        SchemaValidationException exception = new SchemaValidationException(List.of("Node label is required"));

        ProblemDetail problem = handler.handleSchemaValidation(exception, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Schema validation failed");
        assertThat(problem.getDetail()).isEqualTo("Schema validation failed");
        assertThat(problem.getProperties()).containsEntry("errors", List.of("Node label is required"));
    }

    @Test
    void unhandledProblemUsesFallbackMessageWhenExceptionMessageIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/knowledge-bases");

        ProblemDetail problem = handler.handleUnhandled(new RuntimeException(" "), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Unexpected error");
        assertThat(problem.getDetail()).isEqualTo("Unexpected error");
    }
}
