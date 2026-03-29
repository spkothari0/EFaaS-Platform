package com.efaas.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event published when a new tenant is created.
 * Topic: efaas.tenant.created
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantCreatedEvent extends DomainEvent {

    private String tenantName;
    private String email;
    private String planTier;

    public TenantCreatedEvent(UUID tenantId, String tenantName, String email, String planTier) {
        super();
        this.tenantName = tenantName;
        this.email = email;
        this.planTier = planTier;
        initializeEventMetadata(tenantId.toString(), "tenant-service");
    }

    @Override
    public String getEventName() {
        return "tenant.created";
    }
}
