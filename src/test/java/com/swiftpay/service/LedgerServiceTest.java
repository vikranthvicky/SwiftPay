package com.swiftpay.service;

import com.swiftpay.entity.Payment;
import com.swiftpay.entity.User;
import com.swiftpay.entity.Wallet;
import com.swiftpay.event.PaymentInitiatedEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentEventProducer paymentEventProducer;
    @Mock WalletService walletService;

    @InjectMocks LedgerService ledgerService;

    @Test
    void insufficientFundsFailsWithoutChangingBalances() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setTransactionId(tx);
        payment.setSenderId(sender);
        payment.setReceiverId(receiver);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(Payment.PaymentStatus.PENDING);

        Wallet senderWallet = wallet(sender, "100.00");
        Wallet receiverWallet = wallet(receiver, "50.00");

        when(paymentRepository.findByTransactionId(tx)).thenReturn(Optional.of(payment));
        when(walletRepository.findByUserIdForUpdate(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(id.equals(sender) ? senderWallet : receiverWallet);
        });
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(tx, sender, receiver, new BigDecimal("500.00"), "INR");

        ledgerService.processPayment(event);

        assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
        assertEquals(new BigDecimal("100.00"), senderWallet.getBalance());
        assertEquals(new BigDecimal("50.00"), receiverWallet.getBalance());
        verify(walletService, never()).cacheBalance(any());
    }

    private Wallet wallet(UUID userId, String balance) {
        User user = new User();
        user.setId(userId);
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(new BigDecimal(balance));
        wallet.setCurrency("INR");
        return wallet;
    }
}
