package io.github.vfedoriv.graphrag.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import io.github.vfedoriv.graphrag.schema.SchemaValidationException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
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
import org.springframework.dao.OptimisticLockingFailureException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        log.error("Validation failed at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
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
        log.error("Bind error at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
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
        log.error("Constraint violation at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
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
        log.error("Maximum upload size exceeded at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(HttpStatus.CONTENT_TOO_LARGE, "Maximum upload size exceeded", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.error("Illegal argument at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(GraphExtractionValidationException.class)
    public ProblemDetail handleGraphExtractionValidation(
        GraphExtractionValidationException ex,
        HttpServletRequest request
    ) {
        log.error("Graph extraction validation failed at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ProcessingOptionsValidationException.class)
    public ProblemDetail handleProcessingOptionsValidation(
        ProcessingOptionsValidationException ex,
        HttpServletRequest request
    ) {
        log.error("Processing options validation failed at {}: errorCount={}", request.getRequestURI(), ex.getErrors().size());
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
        detail.setProperty("errors", ex.getErrors());
        return detail;
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    public ProblemDetail handleHttpMessageConversion(HttpMessageConversionException ex, HttpServletRequest request) {
        String detailMessage = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
            ? ex.getMostSpecificCause().getMessage()
            : ex.getMessage();
        log.error("Request conversion error at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(
            HttpStatus.BAD_REQUEST,
            "Invalid request payload: " + detailMessage,
            request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandled(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "Unexpected error" : ex.getMessage();
        return baseProblem(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequestURI());
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ProblemDetail handleSchemaValidation(SchemaValidationException ex, HttpServletRequest request) {
        log.error("Schema validation failed at {}: errorCount={}", request.getRequestURI(), ex.getErrors().size());
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, "Schema validation failed", request.getRequestURI());
        detail.setProperty("errors", ex.getErrors());
        return detail;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        log.error("Resource not found at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        log.error("Conflict at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AdvancedSearchResultUnavailableException.class)
    public ProblemDetail handleAdvancedSearchResultUnavailable(
        AdvancedSearchResultUnavailableException ex, HttpServletRequest request
    ) {
        ProblemDetail detail = baseProblem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
        detail.setProperty("runStatus", ex.getStatus());
        detail.setProperty("runStage", ex.getStage());
        return detail;
    }

    @ExceptionHandler(AdvancedSearchCapacityException.class)
    public ProblemDetail handleAdvancedSearchCapacity(AdvancedSearchCapacityException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(
        OptimisticLockingFailureException ex, HttpServletRequest request
    ) {
        log.error("Optimistic locking conflict at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(HttpStatus.CONFLICT, "Resource was concurrently modified", request.getRequestURI());
    }

    @ExceptionHandler(EmbeddingSpaceConflictException.class)
    public ProblemDetail handleEmbeddingSpaceConflict(EmbeddingSpaceConflictException ex, HttpServletRequest request) {
        ProblemDetail detail = baseProblem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
        detail.setProperty("affectedKnowledgeBaseIds", ex.getAffectedKnowledgeBaseIds());
        return detail;
    }

    @ExceptionHandler(KnowledgeBaseNotEmptyException.class)
    public ProblemDetail handleKnowledgeBaseNotEmpty(KnowledgeBaseNotEmptyException ex, HttpServletRequest request) {
        ProblemDetail detail = baseProblem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
        detail.setProperty("remainingDocumentCount", ex.getRemainingDocumentCount());
        return detail;
    }

    @ExceptionHandler(QueryRejectedException.class)
    public ProblemDetail handleQueryRejected(QueryRejectedException ex, HttpServletRequest request) {
        log.error(
            "Query rejected at {}: errorCount={}",
            request.getRequestURI(),
            ex.getErrors().size()
        );
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
        detail.setProperty("errors", ex.getErrors());
        return detail;
    }

    @ExceptionHandler(QueryDeadlineExceededException.class)
    public ProblemDetail handleQueryDeadlineExceeded(QueryDeadlineExceededException ex, HttpServletRequest request) {
        log.warn("Query deadline exceeded at {}", request.getRequestURI());
        return baseProblem(HttpStatus.GATEWAY_TIMEOUT, "Query execution deadline exceeded", request.getRequestURI());
    }

    @ExceptionHandler(SchemaDiscoveryFailedException.class)
    public ProblemDetail handleSchemaDiscoveryFailed(SchemaDiscoveryFailedException ex, HttpServletRequest request) {
        log.error("Schema discovery failed at {}: exceptionType={}", request.getRequestURI(), LogMetadata.exceptionType(ex));
        return baseProblem(HttpStatus.BAD_GATEWAY, ex.getMessage(), request.getRequestURI());
    }

    private ProblemDetail baseProblem(HttpStatus status, String title, String instancePath) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, title);
        detail.setTitle(title);
        detail.setType(URI.create("about:blank"));
        detail.setInstance(URI.create(instancePath));
        return detail;
    }
}
