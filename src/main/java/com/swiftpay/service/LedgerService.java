package com.swiftpay.service;

import com.swiftpay.entity.Payment;
import com.swiftpay.entity.Wallet;
import com.swiftpay.event.PaymentCompletedEvent;
import com.swiftpay.event.PaymentFailedEvent;
import com.swiftpay.event.PaymentInitiatedEvent;
import com.swiftpay.kafka.PaymentEventProducer;
import com.swiftpay.repository.PaymentRepository;
import com.swiftpay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LedgerService {

    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final WalletService walletService;

    public LedgerService(
            WalletRepository walletRepository,
            PaymentRepository paymentRepository,
            PaymentEventProducer paymentEventProducer,
            WalletService walletService
    ) {
        this.walletRepository = walletRepository;
        this.paymentRepository = paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
        this.walletService = walletService;
    }

    @Transactional
    public Payment processPayment(PaymentInitiatedEvent event) {

        Payment payment = paymentRepository
                .findByTransactionId(event.getTransactionId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + event.getTransactionId()
                        )
                );

        // Idempotency protection for duplicate Kafka messages. Both COMPLETED and
        // FAILED are terminal states; only PENDING payments may be processed.
        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            return payment;
        }

        UUID senderId = event.getSenderId();
        UUID receiverId = event.getReceiverId();

        Wallet senderWallet;
        Wallet receiverWallet;

        /*
         * Always lock wallets in the same order.
         * This reduces the possibility of deadlocks when
         * multiple payments are processed concurrently.
         */
        if (senderId.compareTo(receiverId) < 0) {

            senderWallet = walletRepository
                    .findByUserIdForUpdate(senderId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Sender wallet not found: " + senderId
                            )
                    );

            receiverWallet = walletRepository
                    .findByUserIdForUpdate(receiverId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Receiver wallet not found: " + receiverId
                            )
                    );

        } else {

            receiverWallet = walletRepository
                    .findByUserIdForUpdate(receiverId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Receiver wallet not found: " + receiverId
                            )
                    );

            senderWallet = walletRepository
                    .findByUserIdForUpdate(senderId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Sender wallet not found: " + senderId
                            )
                    );
        }

        /*
         * Validate sender currency.
         */
        if (!senderWallet.getCurrency()
                .equalsIgnoreCase(event.getCurrency())) {

            return failPayment(
                    payment,
                    event,
                    "Sender wallet currency does not match payment currency"
            );
        }

        /*
         * Validate receiver currency.
         */
        if (!receiverWallet.getCurrency()
                .equalsIgnoreCase(event.getCurrency())) {

            return failPayment(
                    payment,
                    event,
                    "Receiver wallet currency does not match payment currency"
            );
        }

        /*
         * Final balance check.
         *
         * This check happens inside the database transaction
         * after the wallet has been locked.
         */
        if (senderWallet.getBalance()
                .compareTo(event.getAmount()) < 0) {

            return failPayment(
                    payment,
                    event,
                    "Insufficient funds"
            );
        }

        /*
         * Debit sender.
         */
        senderWallet.setBalance(
                senderWallet.getBalance()
                        .subtract(event.getAmount())
        );

        /*
         * Credit receiver.
         */
        receiverWallet.setBalance(
                receiverWallet.getBalance()
                        .add(event.getAmount())
        );

        /*
         * Mark payment as completed.
         */
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setFailureReason(null);

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);
        walletService.cacheBalance(senderWallet);
        walletService.cacheBalance(receiverWallet);

        Payment savedPayment = paymentRepository.save(payment);

        /*
         * Publish PaymentCompleted event.
         */
        PaymentCompletedEvent completedEvent =
                new PaymentCompletedEvent(
                        event.getTransactionId(),
                        event.getSenderId(),
                        event.getReceiverId(),
                        event.getAmount(),
                        event.getCurrency()
                );

        paymentEventProducer.publishPaymentCompleted(completedEvent);

        return savedPayment;
    }

    private Payment failPayment(
            Payment payment,
            PaymentInitiatedEvent event,
            String reason
    ) {

        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(reason);

        Payment savedPayment = paymentRepository.save(payment);

        /*
         * Publish PaymentFailed event.
         */
        PaymentFailedEvent failedEvent =
                new PaymentFailedEvent(
                        event.getTransactionId(),
                        event.getSenderId(),
                        event.getReceiverId(),
                        event.getAmount(),
                        event.getCurrency(),
                        reason
                );

        paymentEventProducer.publishPaymentFailed(failedEvent);

        return savedPayment;
    }
}