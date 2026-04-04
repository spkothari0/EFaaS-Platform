package com.efaas.payment.exception;

import com.efaas.common.exception.EFaaSException;

public abstract class PaymentException extends EFaaSException {

    protected PaymentException(String message, String errorCode, int httpStatus) {
        super(message, errorCode, httpStatus);
    }
}
