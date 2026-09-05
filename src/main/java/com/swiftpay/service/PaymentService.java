package com.swiftpay.service;

import com.swiftpay.dto.CreatePaymentRequest;
import com.swiftpay.dto.PaymentResponse;
import com.swiftpay.entity.Payment;
import com.swiftpay.entity.Wallet;
import com.swiftpay.event.PaymentInitiatedEvent;
import com.swiftpay.exception.ConflictException;
import com.swiftpay.kafka.PaymentEventProducer;
import com.swiftpay.repository.PaymentRepository;
import com.swiftpay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final RedisIdempotencyService redisIdempotencyService;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentService(
            PaymentRepository paymentRepository,
            WalletRepository walletRepository,
            RedisIdempotencyService redisIdempotencyService,
            PaymentEventProducer paymentEventProducer
    ) {
        this.paymentRepository = paymentRepository;
        this.walletRepository = walletRepository;
        this.redisIdempotencyService = redisIdempotencyService;
        this.paymentEventProducer = paymentEventProducer;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        UUID transactionId = request.getTransactionId();

        Payment existingPayment = paymentRepository.findByTransactionId(transactionId).orElse(null);
        if (existingPayment != null) {
            validateSameRequest(existingPayment, request);

            // A PENDING payment may exist because the caller committed the database
            // record but the Kafka publish failed. Re-publish the event safely; the
            // ledger consumer is idempotent. COMPLETED/FAILED are terminal states.
            if (existingPayment.getStatus() == Payment.PaymentStatus.PENDING) {
                publishInitiatedAfterCommit(toInitiatedEvent(existingPayment));
            }
            return toResponse(existingPayment);
        }

        if (!redisIdempotencyService.reserve(transactionId)) {
            Payment payment = paymentRepository.findByTransactionId(transactionId).orElse(null);
            if (payment != null) {
                validateSameRequest(payment, request);
                return toResponse(payment);
            }
            throw new ConflictException("Payment with this transaction_id is already being processed");
        }

        try {
            if (request.getSenderId().equals(request.getReceiverId())) {
                throw new IllegalArgumentException("Sender and receiver cannot be the same");
            }

            Wallet senderWallet = walletRepository.findByUserId(request.getSenderId())
                    .orElseThrow(() -> new IllegalArgumentException("Sender wallet not found"));
            Wallet receiverWallet = walletRepository.findByUserId(request.getReceiverId())
                    .orElseThrow(() -> new IllegalArgumentException("Receiver wallet not found"));

            if (!senderWallet.getCurrency().equalsIgnoreCase(request.getCurrency())
                    || !receiverWallet.getCurrency().equalsIgnoreCase(request.getCurrency())) {
                throw new IllegalArgumentException("Wallet currency does not match payment currency");
            }

            if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
                throw new IllegalArgumentException("Insufficient funds");
            }

            Payment payment = new Payment();
            payment.setTransactionId(transactionId);
            payment.setSenderId(request.getSenderId());
            payment.setReceiverId(request.getReceiverId());
            payment.setAmount(request.getAmount());
            payment.setCurrency(request.getCurrency());
            payment.setStatus(Payment.PaymentStatus.PENDING);

            Payment savedPayment = paymentRepository.save(payment);

            // Publish only after the PostgreSQL transaction commits. Publishing while
            // the transaction is open can let the Kafka consumer race the DB commit.
            publishInitiatedAfterCommit(toInitiatedEvent(savedPayment));

            return toResponse(savedPayment);
        } catch (Exception ex) {
            redisIdempotencyService.remove(transactionId);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getTransactionHistory(UUID userId) {
        return paymentRepository
                .findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void publishInitiatedAfterCommit(PaymentInitiatedEvent event) {
        Runnable publish = () -> {
            try {
                paymentEventProducer.publishPaymentInitiated(event);
                redisIdempotencyService.markCompleted(event.getTransactionId());
            } catch (Exception ex) {
                // Keep the payment PENDING so a retry with the same transaction_id
                // can safely publish the event again.
                redisIdempotencyService.remove(event.getTransactionId());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private PaymentInitiatedEvent toInitiatedEvent(Payment payment) {
        return new PaymentInitiatedEvent(
                payment.getTransactionId(),
                payment.getSenderId(),
                payment.getReceiverId(),
                payment.getAmount(),
                payment.getCurrency()
        );
    }

    private void validateSameRequest(Payment existing, CreatePaymentRequest request) {
        if (!existing.getSenderId().equals(request.getSenderId())
                || !existing.getReceiverId().equals(request.getReceiverId())
                || existing.getAmount().compareTo(request.getAmount()) != 0
                || !existing.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new ConflictException("transaction_id is already associated with a different payment request");
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getTransactionId(), payment.getSenderId(),
                payment.getReceiverId(), payment.getAmount(), payment.getCurrency(),
                payment.getStatus(), payment.getFailureReason(), payment.getCreatedAt(), payment.getUpdatedAt()
        );
    }
}
