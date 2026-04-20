package com.efaas.lending.repository;

import com.efaas.lending.entity.Loan;
import com.efaas.lending.entity.RepaymentInstallment;
import com.efaas.lending.entity.RepaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepaymentInstallmentRepository extends JpaRepository<RepaymentInstallment, UUID> {

    List<RepaymentInstallment> findByLoanOrderByInstallmentNumberAsc(Loan loan);

    Optional<RepaymentInstallment> findFirstByLoanAndStatusOrderByDueDateAsc(Loan loan, RepaymentStatus status);

    @Query("SELECT r FROM RepaymentInstallment r WHERE r.status = :status AND r.dueDate < :date")
    List<RepaymentInstallment> findByStatusAndDueDateBefore(
            @Param("status") RepaymentStatus status,
            @Param("date") LocalDate date);

    long countByLoanAndStatus(Loan loan, RepaymentStatus status);
}
