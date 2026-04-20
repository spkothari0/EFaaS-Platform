package com.efaas.lending.controller;

import com.efaas.lending.dto.*;
import com.efaas.lending.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan application, disbursement, and repayment schedule management")
public class LoanController {

    private final LoanService loanService;

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Apply for a loan",
            description = "Submits a loan application for the tenant. Runs credit scoring based on the " +
                    "tenant's financial profile and returns an approval decision with proposed terms."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Loan application submitted and decision returned"),
            @ApiResponse(responseCode = "400", description = "Invalid application request"),
            @ApiResponse(responseCode = "422", description = "Loan application declined by credit engine")
    })
    public LoanApplicationResponse applyForLoan(
            @Parameter(hidden = true) @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody LoanApplicationRequest request) {
        return loanService.applyForLoan(tenantId, request);
    }

    @GetMapping("/{loanId}")
    @Operation(
            summary = "Get loan details",
            description = "Returns the current state of a loan including status, approved amount, " +
                    "interest rate, and key dates."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan found"),
            @ApiResponse(responseCode = "404", description = "Loan not found")
    })
    public LoanDetailsResponse getLoan(
            @Parameter(hidden = true) @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Parameter(description = "Loan ID", required = true) @PathVariable UUID loanId) {
        return loanService.getLoan(tenantId, loanId);
    }

    @GetMapping("/{loanId}/schedule")
    @Operation(
            summary = "Get repayment schedule",
            description = "Returns the full amortization schedule for the loan — each installment's " +
                    "due date, principal, interest, and remaining balance."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repayment schedule returned"),
            @ApiResponse(responseCode = "404", description = "Loan not found")
    })
    public RepaymentScheduleResponse getRepaymentSchedule(
            @Parameter(hidden = true) @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Parameter(description = "Loan ID", required = true) @PathVariable UUID loanId) {
        return loanService.getRepaymentSchedule(tenantId, loanId);
    }

    @PostMapping("/{loanId}/disburse")
    @Operation(
            summary = "Disburse a loan",
            description = "Triggers fund disbursement for an approved loan. " +
                    "Loan must be in APPROVED status. Publishes a LoanDisbursedEvent to Kafka."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan disbursed successfully"),
            @ApiResponse(responseCode = "400", description = "Loan is not in a disbursable state"),
            @ApiResponse(responseCode = "404", description = "Loan not found")
    })
    public LoanDetailsResponse disburseLoan(
            @Parameter(hidden = true) @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Parameter(description = "Loan ID", required = true) @PathVariable UUID loanId) {
        return loanService.disburseLoan(tenantId, loanId);
    }
}
