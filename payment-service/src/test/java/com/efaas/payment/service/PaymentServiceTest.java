package com.efaas.payment.service;

import com.efaas.payment.dto.CreatePaymentRequest;
import com.efaas.payment.dto.PaymentResponse;
import com.efaas.payment.entity.Payment;
import com.efaas.payment.entity.PaymentStatus;
import com.efaas.payment.exception.InvalidPaymentStateException;
import com.efaas.payment.exception.PaymentNotFoundException;
import com.efaas.payment.repository.PaymentRepository;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID tenantId;
    private CreatePaymentRequest request;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        request = new CreatePaymentRequest(2000L, "usd", "Test payment", "idem-key-001");
    }

    @Test
    void createPayment_whenIdempotencyKeyExists_returnsExistingPayment() {
        Payment existing = Payment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .idempotencyKey("idem-key-001")
                .stripePaymentIntentId("pi_existing")
                .amount(2000L)
                .currency("usd")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByIdempotencyKey("idem-key-001")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.createPayment(request, tenantId);

        assertThat(response.paymentId()).isEqualTo(existing.getId());
        assertThat(response.stripePaymentIntentId()).isEqualTo("pi_existing");
        // Stripe should NOT be called for a duplicate idempotency key
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_withNewIdempotencyKey_createsStripeIntentAndSaves() throws Exception {
        when(paymentRepository.findByIdempotencyKey("idem-key-001")).thenReturn(Optional.empty());

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_new_123");
        when(mockIntent.getClientSecret()).thenReturn("pi_new_123_secret_abc");

        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .idempotencyKey("idem-key-001")
                .stripePaymentIntentId("pi_new_123")
                .amount(2000L)
                .currency("usd")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        try (MockedStatic<PaymentIntent> mockedStatic = mockStatic(PaymentIntent.class)) {
            mockedStatic.when(() -> PaymentIntent.create(
                    any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenReturn(mockIntent);

            PaymentResponse response = paymentService.createPayment(request, tenantId);

            assertThat(response.stripePaymentIntentId()).isEqualTo("pi_new_123");
            assertThat(response.clientSecret()).isEqualTo("pi_new_123_secret_abc");
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @Test
    void getPayment_whenFound_returnsPayment() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .tenantId(tenantId)
                .stripePaymentIntentId("pi_get_test")
                .amount(2000L)
                .currency("usd")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(paymentId, tenantId);

        assertThat(response.paymentId()).isEqualTo(paymentId);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void getPayment_whenNotFound_throwsNotFoundException() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(paymentId, tenantId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void getPayment_forDifferentTenant_throwsNotFoundException() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .tenantId(UUID.randomUUID())
                .stripePaymentIntentId("pi_other_tenant")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPayment(paymentId, tenantId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void refundPayment_whenPaymentNotFound_throwsNotFoundException() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId, tenantId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void refundPayment_whenPaymentNotCompleted_throwsInvalidStateException() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = Payment.builder()
                .id(paymentId)
                .tenantId(tenantId)
                .status(PaymentStatus.PENDING)
                .stripePaymentIntentId("pi_123")
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId, tenantId))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void refundPayment_forDifferentTenant_throwsNotFoundException() {
        UUID paymentId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .tenantId(otherTenantId)
                .status(PaymentStatus.COMPLETED)
                .stripePaymentIntentId("pi_123")
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId, tenantId))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
