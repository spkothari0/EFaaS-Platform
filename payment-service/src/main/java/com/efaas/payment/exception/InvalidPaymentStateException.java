package com.efaas.payment.exception;

public class InvalidPaymentStateException extends PaymentException {

    public InvalidPaymentStateException(String message) {
        super(message, "INVALID_PAYMENT_STATE", 400);
    }
}
