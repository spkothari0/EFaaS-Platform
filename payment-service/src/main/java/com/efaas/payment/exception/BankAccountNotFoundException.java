package com.efaas.payment.exception;

public class BankAccountNotFoundException extends RuntimeException {

    private static final String ERROR_CODE = "BANK_ACCOUNT_NOT_FOUND";

    public BankAccountNotFoundException(String message) {
        super(message);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
