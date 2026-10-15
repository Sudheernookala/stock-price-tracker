package com.solactive.stocktracker.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 7807 {@link ProblemDetail} responses.
 *
 * <p>Standard Spring MVC errors (404 unknown path, 405, 415, ...) are handled by the parent class.
 * The overrides below give clearer messages for the cases clients hit most often. Unexpected
 * exceptions are logged with a reference id and returned as a generic 500, so internal details
 * never leak to clients.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles all business exceptions.
     *
     * @param ex the exception
     * @return problem response with the status and message carried by the exception
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        String title = switch (ex) {
            case InvalidFileException ignored -> "Bad Request";
            case ResourceNotFoundException ignored -> "Resource Not Found";
            case ScanInProgressException ignored -> "Conflict";
            case ContentInProgressException ignored -> "Conflict";
            case FolderAccessException ignored -> "Folder Not Accessible";
        };
        if (ex.getStatus().is5xxServerError()) {
            log.error("Request failed: {}", ex.getMessage(), ex);
        } else {
            log.info("Request rejected: {}", ex.getMessage());
        }
        return problem(ex.getStatus(), title, ex.getMessage());
    }

    /**
     * Last-resort handler for anything unexpected.
     *
     * @param ex the exception
     * @return generic 500 problem response with a reference id that is also logged
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        String reference = UUID.randomUUID().toString();
        log.error("Unexpected error, reference {}", reference, ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Reference id: " + reference);
    }

    /** {@inheritDoc} */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<String> violations = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .toList();
        ProblemDetail body = problemBody(HttpStatus.BAD_REQUEST, "Bad Request",
                "Request validation failed: " + String.join("; ", violations));
        body.setProperty("errors", violations);
        return widen(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
    }

    /** {@inheritDoc} */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return widen(problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "Parameter '%s' has an invalid value '%s'.".formatted(ex.getPropertyName(), ex.getValue())));
    }

    /** {@inheritDoc} */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            @NonNull MissingServletRequestPartException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return widen(problem(HttpStatus.BAD_REQUEST, "Bad Request", InvalidFileException.missingFile().getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            @NonNull MaxUploadSizeExceededException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return widen(problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                "The uploaded file exceeds the maximum allowed size."));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status).body(problemBody(status, title, detail));
    }

    private static ProblemDetail problemBody(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setType(URI.create("about:blank"));
        body.setProperty("timestamp", Instant.now());
        return body;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseEntity<Object> widen(ResponseEntity<ProblemDetail> response) {
        return (ResponseEntity) response;
    }
}
