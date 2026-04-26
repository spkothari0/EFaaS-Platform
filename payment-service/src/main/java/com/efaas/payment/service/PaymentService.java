package com.efaas.payment.service;

import com.efaas.payment.dto.CreatePaymentRequest;
import com.efaas.payment.dto.PaymentResponse;
import com.efaas.payment.entity.Payment;
import com.efaas.payment.entity.PaymentStatus;
import com.efaas.payment.exception.InvalidPaymentStateException;
import com.efaas.payment.exception.PaymentNotFoundException;
import com.efaas.payment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, UUID tenantId) {
        // Idempotency: return existing payment if the same key was used before
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(existing -> {
                    log.info("Returning existing payment for idempotency key: {}", request.idempotencyKey());
                    return PaymentResponse.from(existing);
                })
                .orElseGet(() -> createNewPayment(request, tenantId));
    }

    private PaymentResponse createNewPayment(CreatePaymentRequest request, UUID tenantId) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(request.amount())
                .setCurrency(request.currency())
                .setDescription(request.description())
                .putMetadata("tenantId", tenantId.toString())
                .putMetadata("idempotencyKey", request.idempotencyKey())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build())
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(request.idempotencyKey())
                .build();

        PaymentIntent intent;
        try {
            intent = PaymentIntent.create(params, requestOptions);
        } catch (StripeException e) {
            log.error("Stripe API error creating payment intent: {}", e.getMessage());
            throw new RuntimeException("Failed to create payment with Stripe: " + e.getMessage(), e);
        }

        Payment payment = Payment.builder()
                .tenantId(tenantId)
                .stripePaymentIntentId(intent.getId())
                .idempotencyKey(request.idempotencyKey())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Created payment {} (Stripe PI: {}) for tenant {}", payment.getId(), intent.getId(), tenantId);

        return PaymentResponse.from(payment, intent.getClientSecret());
    }

    @Transactional
    public PaymentResponse refundPayment(UUID paymentId, UUID tenantId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (!payment.getTenantId().equals(tenantId)) {
            throw new PaymentNotFoundException("Payment not found: " + paymentId);
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidPaymentStateException(
                    "Payment must be COMPLETED to refund, current status: " + payment.getStatus());
        }

        try {
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build());
        } catch (StripeException e) {
            log.error("Stripe API error creating refund for payment {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to create refund with Stripe: " + e.getMessage(), e);
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);
        log.info("Refunded payment {} for tenant {}", paymentId, tenantId);

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, UUID tenantId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (!payment.getTenantId().equals(tenantId)) {
            throw new PaymentNotFoundException("Payment not found: " + paymentId);
        }

        return PaymentResponse.from(payment);
    }
}
