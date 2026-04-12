package com.efaas.payment.repository;

import com.efaas.payment.entity.AchPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AchPaymentRepository extends JpaRepository<AchPayment, UUID> {

    Optional<AchPayment> findByPlaidTransferId(String plaidTransferId);

    Optional<AchPayment> findByIdempotencyKey(String idempotencyKey);

    List<AchPayment> findByTenantId(UUID tenantId);

    Optional<AchPayment> findByIdAndTenantId(UUID id, UUID tenantId);
}
