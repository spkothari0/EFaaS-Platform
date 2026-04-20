package com.efaas.lending.repository;

import com.efaas.lending.entity.Loan;
import com.efaas.lending.entity.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {

    Optional<Loan> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Loan> findByTenantId(UUID tenantId);

}
