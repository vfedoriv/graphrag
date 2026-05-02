package io.github.vfedoriv.graphrag.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI());
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException ex, HttpServletRequest request) {
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, "Invalid request", request.getRequestURI());
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, "Constraint violation", request.getRequestURI());
        detail.setProperty(
            "errors",
            ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList()
        );
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandled(Exception ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request.getRequestURI());
    }

    private ProblemDetail baseProblem(HttpStatus status, String title, String instancePath) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, title);
        detail.setTitle(title);
        detail.setType(URI.create("about:blank"));
        detail.setInstance(URI.create(instancePath));
        return detail;
    }
}
