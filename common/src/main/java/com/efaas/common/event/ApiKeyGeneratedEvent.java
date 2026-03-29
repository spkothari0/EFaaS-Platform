package com.efaas.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event published when a new API key is generated for a tenant.
 * Topic: efaas.apikey.generated
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ApiKeyGeneratedEvent extends DomainEvent {

    private UUID keyId;
    private String maskedKey;
    private String planTier;

    public ApiKeyGeneratedEvent(UUID tenantId, UUID keyId, String maskedKey, String planTier) {
        super();
        this.keyId = keyId;
        this.maskedKey = maskedKey;
        this.planTier = planTier;
        initializeEventMetadata(tenantId.toString(), "tenant-service");
    }

    @Override
    public String getEventName() {
        return "apikey.generated";
    }
}
