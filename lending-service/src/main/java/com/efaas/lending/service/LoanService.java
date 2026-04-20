package com.efaas.lending.service;

import com.efaas.common.dto.FinancialProfile;
import com.efaas.common.event.LoanAppliedEvent;
import com.efaas.common.event.LoanApprovedEvent;
import com.efaas.common.event.LoanDeniedEvent;
import com.efaas.lending.client.FinancialProfileClient;
import com.efaas.lending.dto.*;
import com.efaas.lending.engine.CreditScoringEngine;
import com.efaas.lending.entity.*;
import com.efaas.lending.exception.LoanNotFoundException;
import com.efaas.lending.kafka.LoanEventPublisher;
import com.efaas.lending.repository.LoanRepository;
import com.efaas.lending.repository.RepaymentInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private static final double DEFAULT_ANNUAL_RATE = 12.0; // 12% p.a. for all approved loans

    private final LoanRepository loanRepository;
    private final RepaymentInstallmentRepository installmentRepository;
    private final CreditScoringEngine creditScoringEngine;
    private final AmortizationService amortizationService;
    private final FinancialProfileClient financialProfileClient;
    private final LoanEventPublisher eventPublisher;

    // ─── Apply ───────────────────────────────────────────────────────────────────

    @Transactional
    public LoanApplicationResponse applyForLoan(UUID tenantId, LoanApplicationRequest request) {
        // 1. Fetch financial profile from payment-service
        FinancialProfile profile = financialProfileClient.getFinancialProfile(tenantId, request.plaidAccountId());

        // 2. Score
        int score = creditScoringEngine.score(profile, request.requestedAmountCents(), request.termMonths());
        LoanStatus status = determineStatus(score);
        String reason = buildDecisionReason(score, status);

        log.info("Loan application for tenant {}: score={}, status={}", tenantId, score, status);

        // 3. Persist loan
        Loan loan = Loan.builder()
                .tenantId(tenantId)
                .applicantUserId(request.applicantUserId())
                .plaidAccountId(request.plaidAccountId())
                .principalAmountCents(request.requestedAmountCents())
                .annualInterestRate(DEFAULT_ANNUAL_RATE)
                .termMonths(request.termMonths())
                .status(status)
                .creditScore(score)
                .purpose(request.purpose())
                .decisionReason(reason)
                .build();

        loan = loanRepository.save(loan);

        // 4. Generate amortization schedule for approved loans
        Long monthlyPaymentCents = null;
        if (status == LoanStatus.APPROVED || status == LoanStatus.CONDITIONALLY_APPROVED) {
            List<RepaymentInstallment> schedule = amortizationService.generateSchedule(loan);
            loanRepository.save(loan); // save updated monthlyPaymentCents
            installmentRepository.saveAll(schedule);
            monthlyPaymentCents = loan.getMonthlyPaymentCents();
        }

        // 5. Publish Kafka event
        eventPublisher.publishLoanApplied(new LoanAppliedEvent(
                tenantId, loan.getId(), score, request.requestedAmountCents(), status.name()));

        if (status == LoanStatus.APPROVED || status == LoanStatus.CONDITIONALLY_APPROVED) {
            eventPublisher.publishLoanApproved(new LoanApprovedEvent(
                    tenantId, loan.getId(), score, request.requestedAmountCents(),
                    request.termMonths(), monthlyPaymentCents != null ? monthlyPaymentCents : 0));
        } else {
            eventPublisher.publishLoanDenied(new LoanDeniedEvent(tenantId, loan.getId(), score, reason));
        }

        return new LoanApplicationResponse(loan.getId(), status.name(), score, reason, monthlyPaymentCents);
    }

    // ─── Get Loan ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LoanDetailsResponse getLoan(UUID tenantId, UUID loanId) {
        Loan loan = findLoan(tenantId, loanId);
        return toDetailsResponse(loan);
    }

    // ─── Get Schedule ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RepaymentScheduleResponse getRepaymentSchedule(UUID tenantId, UUID loanId) {
        Loan loan = findLoan(tenantId, loanId);
        List<RepaymentInstallment> installments = installmentRepository.findByLoanOrderByInstallmentNumberAsc(loan);

        long total = installments.stream().mapToLong(RepaymentInstallment::getTotalCents).sum();
        List<InstallmentDto> dtos = installments.stream().map(this::toInstallmentDto).toList();

        return new RepaymentScheduleResponse(loanId, total, dtos);
    }

    // ─── Disburse ─────────────────────────────────────────────────────────────────

    @Transactional
    public LoanDetailsResponse disburseLoan(UUID tenantId, UUID loanId) {
        Loan loan = findLoan(tenantId, loanId);

        if (loan.getStatus() != LoanStatus.APPROVED && loan.getStatus() != LoanStatus.CONDITIONALLY_APPROVED) {
            throw new IllegalStateException("Loan " + loanId + " is not in an approvable state: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDisbursedAt(ZonedDateTime.now());
        loanRepository.save(loan);

        log.info("Loan {} disbursed for tenant {}", loanId, tenantId);
        return toDetailsResponse(loan);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Loan findLoan(UUID tenantId, UUID loanId) {
        return loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new LoanNotFoundException("Loan not found: " + loanId));
    }

    private LoanStatus determineStatus(int score) {
        if (score >= CreditScoringEngine.APPROVE_THRESHOLD) return LoanStatus.APPROVED;
        if (score >= CreditScoringEngine.CONDITIONAL_THRESHOLD) return LoanStatus.CONDITIONALLY_APPROVED;
        return LoanStatus.DENIED;
    }

    private String buildDecisionReason(int score, LoanStatus status) {
        return switch (status) {
            case APPROVED -> "Credit score " + score + " meets approval threshold (>=700)";
            case CONDITIONALLY_APPROVED -> "Credit score " + score + " qualifies for conditional approval (500-699). Additional verification may be required.";
            case DENIED -> "Credit score " + score + " is below minimum threshold (<500). Insufficient financial profile.";
            default -> "Decision: " + status.name();
        };
    }

    private LoanDetailsResponse toDetailsResponse(Loan loan) {
        return new LoanDetailsResponse(
                loan.getId(), loan.getTenantId(), loan.getApplicantUserId(),
                loan.getPrincipalAmountCents(), loan.getAnnualInterestRate(),
                loan.getTermMonths(), loan.getStatus().name(), loan.getCreditScore(),
                loan.getMonthlyPaymentCents(), loan.getPurpose(),
                loan.getDecisionReason(), loan.getCreatedAt());
    }

    private InstallmentDto toInstallmentDto(RepaymentInstallment i) {
        return new InstallmentDto(
                i.getInstallmentNumber(), i.getDueDate(),
                i.getPrincipalCents(), i.getInterestCents(),
                i.getTotalCents(), i.getStatus().name());
    }
}
