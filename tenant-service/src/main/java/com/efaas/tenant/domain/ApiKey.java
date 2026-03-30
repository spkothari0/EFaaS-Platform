package com.efaas.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * API Key Entity - Stores hashed API keys for tenant authentication.
 * Never stores plaintext keys. Keys are returned only once upon creation.
 */
@Entity
@Table(name = "api_keys", indexes = {
    @Index(name = "idx_api_keys_tenant", columnList = "tenant_id"),
    @Index(name = "idx_api_keys_hash_active", columnList = "key_hash,is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @Column(columnDefinition = "UUID")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, columnDefinition = "UUID")
    private UUID tenantId;

    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;  // SHA-256 hash of the API key

    @Column(name = "masked_key", nullable = false, length = 32)
    private String maskedKey;  // e.g., "efaas_live_***...a5c9b"

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;  // Optional: for key expiration

    /**
     * Check if the key is currently valid.
     * Valid = active AND not expired.
     */
    public boolean isValid() {
        if (!this.active) {
            return false;
        }
        if (this.expiresAt != null && ZonedDateTime.now().isAfter(this.expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Helper to create masked version of API key for display.
     * Example: "efaas_live_1234567890...a5c9b" (shows first 11 and last 5 chars)
     */
    public static String maskKey(String plainKey) {
        if (plainKey == null || plainKey.length() < 16) {
            return "***";
        }
        return plainKey.substring(0, 8) + "***" + plainKey.substring(plainKey.length() - 5);
    }
}
