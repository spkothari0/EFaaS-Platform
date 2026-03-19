package com.efaas.common.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Base class for all domain events in the EFaaS platform.
 * All events are immutable and serializable for Kafka transport.
 * Ensures consistent event structure across all microservices.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public abstract class DomainEvent {

    private UUID eventId;
    private ZonedDateTime timestamp;
    private String tenantId;
    private String sourceService;  // e.g., "payment-service", "lending-service"

    /**
     * Initializes common event fields.
     * Called by subclass constructors to ensure every event has an ID and timestamp.
     */
    protected void initializeEventMetadata(String tenantId, String sourceService) {
        if (this.eventId == null) {
            this.eventId = UUID.randomUUID();
        }
        if (this.timestamp == null) {
            this.timestamp = ZonedDateTime.now();
        }
        this.tenantId = tenantId;
        this.sourceService = sourceService;
    }

    /**
     * Event name for Kafka topics, logging, and routing.
     * Override in subclasses to provide specific event names.
     */
    public abstract String getEventName();
}
