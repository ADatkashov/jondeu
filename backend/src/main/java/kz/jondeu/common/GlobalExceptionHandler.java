package kz.jondeu.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(Instant timestamp, int status, String code, String message, Map<String, String> fields) {}

    private ResponseEntity<ErrorBody> body(HttpStatus s, String code, String msg, Map<String, String> fields) {
        return ResponseEntity.status(s).body(new ErrorBody(Instant.now(), s.value(), code, msg, fields));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException e) { return body(e.status(), e.code(), e.getMessage(), null); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        Map<String, String> f = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(x -> f.putIfAbsent(x.getField(), x.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", f);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> constraint(ConstraintViolationException e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getMessage(), null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> denied(AccessDeniedException e) { return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", null); }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorBody> optimistic(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "The resource was modified by someone else, reload and retry", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "CONFLICT", "Operation conflicts with existing data", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File is too large", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> other(Exception e) {
        log.error("Unhandled error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", null);
    }
}
