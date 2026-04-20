package com.efaas.lending.service;

import com.efaas.common.event.PaymentCompletedEvent;
import com.efaas.lending.entity.*;
import com.efaas.lending.repository.LoanRepository;
import com.efaas.lending.repository.RepaymentInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Matches incoming Stripe payment events to outstanding loan installments.
 * Matching strategy: find the tenant's oldest PENDING installment (earliest due date).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRepaymentService {

    private final LoanRepository loanRepository;
    private final RepaymentInstallmentRepository installmentRepository;

    @Transactional
    public void matchPayment(PaymentCompletedEvent event) {
        UUID tenantId = UUID.fromString(event.getTenantId());

        // Find ACTIVE or DISBURSED loans for this tenant
        List<Loan> activeLoans = loanRepository.findByTenantId(tenantId).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.DISBURSED)
                .toList();

        if (activeLoans.isEmpty()) {
            log.debug("No active loans for tenant {} — payment event ignored", tenantId);
            return;
        }

        for (Loan loan : activeLoans) {
            // Activate disbursed loan on first payment
            if (loan.getStatus() == LoanStatus.DISBURSED) {
                loan.setStatus(LoanStatus.ACTIVE);
                loanRepository.save(loan);
            }

            installmentRepository.findFirstByLoanAndStatusOrderByDueDateAsc(loan, RepaymentStatus.PENDING)
                    .ifPresent(installment -> {
                        installment.setStatus(RepaymentStatus.PAID);
                        installment.setPaidAt(ZonedDateTime.now());
                        installment.setStripePaymentIntentId(event.getStripePaymentIntentId());
                        installmentRepository.save(installment);
                        log.info("Installment {} of loan {} marked PAID via payment {}",
                                installment.getInstallmentNumber(), loan.getId(), event.getStripePaymentIntentId());

                        checkLoanCompletion(loan);
                    });
        }
    }

    private void checkLoanCompletion(Loan loan) {
        long remaining = installmentRepository.countByLoanAndStatus(loan, RepaymentStatus.PENDING);
        if (remaining == 0) {
            loan.setStatus(LoanStatus.COMPLETED);
            loanRepository.save(loan);
            log.info("Loan {} fully repaid — status set to COMPLETED", loan.getId());
        }
    }
}
