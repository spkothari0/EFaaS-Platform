package com.efaas.lending.job;

import com.efaas.lending.entity.LoanStatus;
import com.efaas.lending.entity.RepaymentInstallment;
import com.efaas.lending.entity.RepaymentStatus;
import com.efaas.lending.repository.LoanRepository;
import com.efaas.lending.repository.RepaymentInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueDetectionJob {

    private final RepaymentInstallmentRepository installmentRepository;
    private final LoanRepository loanRepository;

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void detectOverdueInstallments() {
        LocalDate today = LocalDate.now();
        List<RepaymentInstallment> overdue =
                installmentRepository.findByStatusAndDueDateBefore(RepaymentStatus.PENDING, today);

        if (overdue.isEmpty()) {
            log.debug("Overdue detection: no overdue installments found");
            return;
        }

        log.info("Overdue detection: marking {} installments as OVERDUE", overdue.size());

        for (RepaymentInstallment installment : overdue) {
            installment.setStatus(RepaymentStatus.OVERDUE);
            installmentRepository.save(installment);

            var loan = installment.getLoan();
            if (loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.DISBURSED) {
                loan.setStatus(LoanStatus.OVERDUE);
                loanRepository.save(loan);
                log.warn("Loan {} marked OVERDUE — installment {} past due date {}",
                        loan.getId(), installment.getInstallmentNumber(), installment.getDueDate());
            }
        }
    }
}
