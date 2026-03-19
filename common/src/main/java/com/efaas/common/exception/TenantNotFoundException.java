package com.efaas.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a tenant is not found in the database.
 */
public class TenantNotFoundException extends EFaaSException {
    public TenantNotFoundException(String tenantId) {
        super(
            String.format("Tenant with ID '%s' not found", tenantId),
            "TENANT_NOT_FOUND",
            HttpStatus.NOT_FOUND.value()
        );
    }
}
