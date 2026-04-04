package com.efaas.payment.controller;

import com.efaas.payment.dto.CreatePaymentRequest;
import com.efaas.payment.dto.PaymentResponse;
import com.efaas.payment.service.PaymentService;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Stripe payment intent management")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a payment",
            description = "Creates a Stripe PaymentIntent and stores a PENDING payment record. Returns clientSecret for frontend confirmation.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Idempotency key already used (returns existing payment)")
    })
    public PaymentResponse createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @Parameter(description = "Tenant ID injected by the gateway", required = true)
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return paymentService.createPayment(request, tenantId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public PaymentResponse getPayment(
            @PathVariable("id") UUID id,
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return paymentService.getPayment(id, tenantId);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a payment",
            description = "Issues a full refund via Stripe. Payment must be in COMPLETED status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund initiated"),
            @ApiResponse(responseCode = "400", description = "Payment not in COMPLETED status"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public PaymentResponse refundPayment(
            @PathVariable("id") UUID id,
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return paymentService.refundPayment(id, tenantId);
    }
}
