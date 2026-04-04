package com.efaas.payment.exception;

public class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(String message) {
        super(message, "PAYMENT_NOT_FOUND", 404);
    }
}
