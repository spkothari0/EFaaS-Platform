package com.efaas.lending.exception;

import lombok.Getter;

@Getter
public class LoanNotFoundException extends RuntimeException {
    private final String errorCode = "LOAN_NOT_FOUND";

    public LoanNotFoundException(String message) {
        super(message);
    }
}
