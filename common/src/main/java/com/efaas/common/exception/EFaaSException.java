package com.efaas.common.exception;

/**
 * Base exception for all custom business exceptions in the EFaaS platform.
 * Extends RuntimeException for unchecked exception behavior.
 */
public abstract class EFaaSException extends RuntimeException {
    private final String errorCode;
    private final int httpStatus;

    public EFaaSException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public EFaaSException(String message, Throwable cause, String errorCode, int httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
