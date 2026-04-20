package com.efaas.lending.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class LoanExceptionHandler {

    @ExceptionHandler(LoanNotFoundException.class)
    public ProblemDetail handleNotFound(LoanNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setType(URI.create("https://efaas.io/errors/loan-not-found"));
        p.setProperty("errorCode", ex.getErrorCode());
        return p;
    }

    @ExceptionHandler(CreditDeniedException.class)
    public ProblemDetail handleDenied(CreditDeniedException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setType(URI.create("https://efaas.io/errors/credit-denied"));
        p.setProperty("errorCode", ex.getErrorCode());
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        p.setType(URI.create("https://efaas.io/errors/validation-failed"));
        p.setProperty("errorCode", "VALIDATION_FAILED");
        return p;
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex) {
        log.error("Unexpected error in lending service", ex);
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        p.setType(URI.create("https://efaas.io/errors/internal-error"));
        p.setProperty("errorCode", "INTERNAL_ERROR");
        return p;
    }
}
