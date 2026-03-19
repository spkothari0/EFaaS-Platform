package com.efaas.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an API key is invalid, expired, or inactive.
 */
public class InvalidApiKeyException extends EFaaSException {
    public InvalidApiKeyException(String message) {
        super(
            message,
            "INVALID_API_KEY",
            HttpStatus.UNAUTHORIZED.value()
        );
    }

    public static InvalidApiKeyException expired() {
        return new InvalidApiKeyException("API key has expired");
    }

    public static InvalidApiKeyException notFound() {
        return new InvalidApiKeyException("API key not found or inactive");
    }

    public static InvalidApiKeyException malformed() {
        return new InvalidApiKeyException("API key is malformed");
    }
}
