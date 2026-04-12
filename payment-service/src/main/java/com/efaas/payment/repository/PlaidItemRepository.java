package com.efaas.payment.repository;

import com.efaas.payment.entity.PlaidItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaidItemRepository extends JpaRepository<PlaidItem, UUID> {

    Optional<PlaidItem> findByTenantIdAndPlaidItemId(UUID tenantId, String plaidItemId);

    Optional<PlaidItem> findByPlaidItemId(String plaidItemId);

    List<PlaidItem> findByTenantId(UUID tenantId);
}
