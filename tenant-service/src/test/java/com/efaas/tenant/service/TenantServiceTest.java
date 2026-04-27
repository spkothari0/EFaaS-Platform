package com.efaas.tenant.service;

import com.efaas.common.dto.TenantDTO;
import com.efaas.common.exception.TenantNotFoundException;
import com.efaas.tenant.domain.Tenant;
import com.efaas.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    TenantRepository tenantRepository;

    @InjectMocks
    TenantService tenantService;

    @Test
    void createTenant_savesAndReturnsDto() {
        Tenant saved = Tenant.builder()
                .name("Acme Corp")
                .email("acme@example.com")
                .plan(Tenant.PlanTier.BASIC)
                .active(true)
                .build();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);

        TenantDTO result = tenantService.createTenant("Acme Corp", "acme@example.com", Tenant.PlanTier.BASIC);

        assertThat(result.getName()).isEqualTo("Acme Corp");
        assertThat(result.getEmail()).isEqualTo("acme@example.com");
        assertThat(result.getPlan()).isEqualTo(TenantDTO.PlanTier.BASIC);
        assertThat(result.isActive()).isTrue();
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void getAllTenants_returnsAllAsDtos() {
        Tenant t1 = Tenant.builder().name("T1").email("t1@t.com").plan(Tenant.PlanTier.FREE).active(true).build();
        Tenant t2 = Tenant.builder().name("T2").email("t2@t.com").plan(Tenant.PlanTier.PRO).active(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(t1, t2));

        List<TenantDTO> result = tenantService.getAllTenants();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TenantDTO::getName).containsExactly("T1", "T2");
    }

    @Test
    void getTenant_returnsDto_whenFound() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .name("Acme")
                .email("acme@example.com")
                .plan(Tenant.PlanTier.FREE)
                .active(true)
                .build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantDTO result = tenantService.getTenant(tenantId);

        assertThat(result.getName()).isEqualTo("Acme");
    }

    @Test
    void getTenant_throwsTenantNotFoundException_whenNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(tenantId))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void updateTenant_updatesOnlyNonNullFields() {
        UUID tenantId = UUID.randomUUID();
        Tenant existing = Tenant.builder()
                .name("Old Name")
                .email("old@example.com")
                .plan(Tenant.PlanTier.FREE)
                .active(true)
                .build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantDTO result = tenantService.updateTenant(tenantId, "New Name", null, null);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getEmail()).isEqualTo("old@example.com");
        assertThat(result.getPlan()).isEqualTo(TenantDTO.PlanTier.FREE);
    }

    @Test
    void updateTenant_throwsTenantNotFoundException_whenNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.updateTenant(tenantId, "Name", null, null))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void toggleTenantStatus_deactivatesTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .name("Acme")
                .email("acme@example.com")
                .plan(Tenant.PlanTier.PRO)
                .active(true)
                .build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantDTO result = tenantService.toggleTenantStatus(tenantId, false);

        assertThat(result.isActive()).isFalse();
    }

    @Test
    void deleteTenant_deletesSuccessfully() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(true);

        tenantService.deleteTenant(tenantId);

        verify(tenantRepository).deleteById(tenantId);
    }

    @Test
    void deleteTenant_throwsTenantNotFoundException_whenNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> tenantService.deleteTenant(tenantId))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
