package com.efaas.payment.repository;

import com.efaas.payment.entity.PlaidAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaidAccountRepository extends JpaRepository<PlaidAccount, UUID> {

    List<PlaidAccount> findByPlaidItem_Id(UUID plaidItemDbId);

    Optional<PlaidAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT a FROM PlaidAccount a WHERE a.tenantId = :tenantId AND a.active = true")
    List<PlaidAccount> findActiveByTenantId(@Param("tenantId") UUID tenantId);
}
