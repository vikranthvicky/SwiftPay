package com.swiftpay.kafka;

import com.swiftpay.event.PaymentInitiatedEvent;
import com.swiftpay.service.LedgerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final LedgerService ledgerService;

    public PaymentEventConsumer(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @KafkaListener(
            topics = "payment.initiated",
            groupId = "swiftpay-ledger"
    )
    public void consumePaymentInitiated(
            PaymentInitiatedEvent event
    ) {

        log.info("Received PaymentInitiated event transactionId={}", event.getTransactionId());

        ledgerService.processPayment(event);

        log.info("Ledger processing completed transactionId={}", event.getTransactionId());
    }
}

