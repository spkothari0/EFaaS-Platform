package com.efaas.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Canonical service identifiers used in domain events and inter-service communication.
 */
public enum ServiceName {

    TENANT_SERVICE("tenant-service"),
    PAYMENT_SERVICE("payment-service"),
    LENDING_SERVICE("lending-service"),
    INVESTMENT_SERVICE("investment-service"),
    FRAUD_SERVICE("fraud-service");

    private final String value;

    ServiceName(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ServiceName from(String value) {
        for (ServiceName s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown service name: " + value);
    }
}
