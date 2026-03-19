package com.efaas.tenant.exception;

import com.efaas.common.exception.EFaaSException;
import com.efaas.common.exception.InvalidApiKeyException;
import com.efaas.common.exception.TenantNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for all microservices.
 * Returns RFC 7807 ProblemDetail responses with consistent error format.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    public ProblemDetail handleTenantNotFound(TenantNotFoundException ex) {
        log.debug("Tenant not found: {}", ex.getMessage());
        return buildProblemDetail(
            ex.getHttpStatus(),
            "Tenant Not Found",
            ex.getMessage(),
            ex.getErrorCode()
        );
    }

    @ExceptionHandler(InvalidApiKeyException.class)
    public ProblemDetail handleInvalidApiKey(InvalidApiKeyException ex) {
        log.debug("Invalid API key: {}", ex.getMessage());
        return buildProblemDetail(
            ex.getHttpStatus(),
            "Invalid API Key",
            ex.getMessage(),
            ex.getErrorCode()
        );
    }

    @ExceptionHandler(EFaaSException.class)
    public ProblemDetail handleEFaaSException(EFaaSException ex) {
        log.error("EFaaS exception: {}", ex.getMessage(), ex);
        return buildProblemDetail(
            ex.getHttpStatus(),
            "Business Logic Error",
            ex.getMessage(),
            ex.getErrorCode()
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.debug("Validation error: {}", ex.getMessage());
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");

        return ResponseEntity.status(status)
            .body(buildProblemDetail(400, "Validation Error", details, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return buildProblemDetail(
            500,
            "Internal Server Error",
            "An unexpected error occurred",
            "INTERNAL_ERROR"
        );
    }

    /**
     * Build a RFC 7807 ProblemDetail response.
     */
    private ProblemDetail buildProblemDetail(int status, String title, String detail, String errorCode) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://api.efaas.io/errors/" + errorCode));
        pd.setProperty("errorCode", errorCode);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
