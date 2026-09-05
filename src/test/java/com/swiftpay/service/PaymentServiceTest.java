package com.swiftpay.service;

import com.swiftpay.dto.CreatePaymentRequest;
import com.swiftpay.entity.Payment;
import com.swiftpay.exception.ConflictException;
import com.swiftpay.kafka.PaymentEventProducer;
import com.swiftpay.repository.PaymentRepository;
import com.swiftpay.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock WalletRepository walletRepository;
    @Mock RedisIdempotencyService redisIdempotencyService;
    @Mock PaymentEventProducer paymentEventProducer;

    @InjectMocks PaymentService paymentService;

    @Test
    void duplicateTransactionWithDifferentPayloadIsRejected() {
        UUID tx = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        Payment existing = new Payment();
        existing.setTransactionId(tx);
        existing.setSenderId(sender);
        existing.setReceiverId(receiver);
        existing.setAmount(new BigDecimal("100.00"));
        existing.setCurrency("INR");
        existing.setStatus(Payment.PaymentStatus.PENDING);

        when(paymentRepository.findByTransactionId(tx)).thenReturn(Optional.of(existing));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setTransactionId(tx);
        request.setSenderId(sender);
        request.setReceiverId(receiver);
        request.setAmount(new BigDecimal("200.00"));
        request.setCurrency("INR");

        assertThrows(ConflictException.class, () -> paymentService.createPayment(request));
        verifyNoInteractions(walletRepository, paymentEventProducer);
    }

    @Test
    void sameTransactionWithSamePayloadReturnsExistingPayment() {
        UUID tx = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        Payment existing = new Payment();
        existing.setId(UUID.randomUUID());
        existing.setTransactionId(tx);
        existing.setSenderId(sender);
        existing.setReceiverId(receiver);
        existing.setAmount(new BigDecimal("100.00"));
        existing.setCurrency("INR");
        existing.setStatus(Payment.PaymentStatus.COMPLETED);

        when(paymentRepository.findByTransactionId(tx)).thenReturn(Optional.of(existing));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setTransactionId(tx);
        request.setSenderId(sender);
        request.setReceiverId(receiver);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("INR");

        assertEquals(existing.getTransactionId(), paymentService.createPayment(request).getTransactionId());
        verifyNoInteractions(walletRepository, redisIdempotencyService, paymentEventProducer);
    }
}
