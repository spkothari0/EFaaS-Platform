package com.efaas.payment.exception;

import lombok.Getter;

@Getter
public class PlaidException extends RuntimeException {

    private final String errorCode;

    public PlaidException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public PlaidException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
