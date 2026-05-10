package io.github.vfedoriv.graphrag.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import io.github.vfedoriv.graphrag.schema.SchemaValidationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum upload size exceeded", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
        return baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    public ProblemDetail handleHttpMessageConversion(HttpMessageConversionException ex, HttpServletRequest request) {
        String detailMessage = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
            ? ex.getMostSpecificCause().getMessage()
            : ex.getMessage();
        log.warn("Request conversion error at {}: {}", request.getRequestURI(), detailMessage);
        return baseProblem(
            HttpStatus.BAD_REQUEST,
            "Invalid request payload: " + detailMessage,
            request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandled(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error at {}", request.getRequestURI(), ex);
        String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "Unexpected error" : ex.getMessage();
        return baseProblem(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequestURI());
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ProblemDetail handleSchemaValidation(SchemaValidationException ex, HttpServletRequest request) {
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, "Schema validation failed", request.getRequestURI());
        detail.setProperty("errors", ex.getErrors());
        return detail;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(QueryRejectedException.class)
    public ProblemDetail handleQueryRejected(QueryRejectedException ex, HttpServletRequest request) {
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
        detail.setProperty("errors", ex.getErrors());
        return detail;
    }

    private ProblemDetail baseProblem(HttpStatus status, String title, String instancePath) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, title);
        detail.setTitle(title);
        detail.setType(URI.create("about:blank"));
        detail.setInstance(URI.create(instancePath));
        return detail;
    }
}
