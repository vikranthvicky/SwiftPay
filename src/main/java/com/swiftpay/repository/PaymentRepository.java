package com.swiftpay.repository;

import com.swiftpay.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByTransactionId(UUID transactionId);

    boolean existsByTransactionId(UUID transactionId);

    List<Payment> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(
            UUID senderId,
            UUID receiverId
    );
}