package com.efaas.tenant.service;

import com.efaas.common.dto.TenantDTO;
import com.efaas.common.exception.TenantNotFoundException;
import com.efaas.tenant.domain.Tenant;
import com.efaas.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing tenant lifecycle (create, read, update, delete).
 * Handles business logic and data validation.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;

    /**
     * Create a new tenant.
     */
    public TenantDTO createTenant(String name, String email, Tenant.PlanTier plan) {
        Tenant tenant = Tenant.builder()
            .name(name)
            .email(email)
            .plan(plan)
            .active(true)
            .build();

        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant created: id={}, name={}, email={}, plan={}", saved.getId(), saved.getName(), saved.getEmail(), saved.getPlan());
        return toDTO(saved);
    }

    /**
     * Retrieve a tenant by ID.
     */
    @Transactional(readOnly = true)
    public TenantDTO getTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
        return toDTO(tenant);
    }

    /**
     * Update an existing tenant.
     */
    public TenantDTO updateTenant(UUID tenantId, String name, String email, Tenant.PlanTier plan) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        if (name != null && !name.isBlank()) {
            tenant.setName(name);
        }
        if (email != null && !email.isBlank()) {
            tenant.setEmail(email);
        }
        if (plan != null) {
            tenant.setPlan(plan);
        }

        Tenant updated = tenantRepository.save(tenant);
        log.info("Tenant updated: id={}", updated.getId());
        return toDTO(updated);
    }

    /**
     * Activate or deactivate a tenant.
     */
    public TenantDTO toggleTenantStatus(UUID tenantId, boolean active) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        tenant.setActive(active);
        Tenant updated = tenantRepository.save(tenant);
        log.info("Tenant status updated: id={}, active={}", updated.getId(), updated.isActive());
        return toDTO(updated);
    }

    /**
     * Delete a tenant (hard delete).
     */
    public void deleteTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException(tenantId.toString());
        }
        tenantRepository.deleteById(tenantId);
        log.info("Tenant deleted: id={}", tenantId);
    }

    /**
     * Convert Tenant entity to DTO.
     */
    private TenantDTO toDTO(Tenant tenant) {
        return TenantDTO.builder()
            .id(tenant.getId())
            .name(tenant.getName())
            .email(tenant.getEmail())
            .plan(TenantDTO.PlanTier.valueOf(tenant.getPlan().name()))
            .active(tenant.isActive())
            .createdAt(tenant.getCreatedAt())
            .updatedAt(tenant.getUpdatedAt())
            .build();
    }
}
