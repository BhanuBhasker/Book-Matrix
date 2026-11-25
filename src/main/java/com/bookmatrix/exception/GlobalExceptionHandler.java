package com.bookmatrix.exception;

import com.bookmatrix.util.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Turns every exception thrown by a controller into the same JSON shape:
 * { "success": false, "error": { "code": "...", "message": "...", "fields": {...} } }
 *
 * Internal details (SQL, stack traces) are logged, never sent to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** One handler for ALL our own exceptions, thanks to polymorphism (status() / code()). */
    @ExceptionHandler(BookMatrixException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookMatrix(BookMatrixException ex, HttpServletRequest request) {
        if (ex instanceof DatabaseException) {
            log.error("Database problem on {} {}", request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.debug("Request rejected: {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
        Map<String, String> fields = (ex instanceof ValidationException ve) ? ve.getFieldErrors() : Map.of();
        return ResponseEntity.status(ex.status()).body(ApiResponse.error(ex.code(), ex.getMessage(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is missing or is not valid JSON of the expected shape.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadParameter(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "One of the request parameters is missing or invalid.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This HTTP method is not supported here.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send the request as application/json.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The file is too large. The limit is 1 MB.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        return error(HttpStatus.BAD_REQUEST, "UPLOAD_FAILED", "The file upload could not be read.");
    }

    /** Unknown API paths get JSON; unknown pages get the friendly 404.html page. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Nothing exists at this address.");
        }
        String page = new ClassPathResource("static/404.html").getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body(page);
    }

    /** Last line of defence: log the real problem, show a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side. Please try again.");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message, Map.of()));
    }
}
