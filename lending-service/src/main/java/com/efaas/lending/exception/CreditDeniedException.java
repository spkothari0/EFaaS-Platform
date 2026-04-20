package com.efaas.lending.exception;

import lombok.Getter;

@Getter
public class CreditDeniedException extends RuntimeException {
    private final String errorCode = "CREDIT_DENIED";

    public CreditDeniedException(String message) {
        super(message);
    }
}
