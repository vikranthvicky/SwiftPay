package com.swiftpay.kafka;

import com.swiftpay.event.PaymentCompletedEvent;
import com.swiftpay.event.PaymentFailedEvent;
import com.swiftpay.event.PaymentInitiatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PaymentEventProducer {

    private static final String PAYMENT_INITIATED_TOPIC = "payment.initiated";
    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        send(PAYMENT_INITIATED_TOPIC, event.getTransactionId().toString(), event);
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        send(PAYMENT_COMPLETED_TOPIC, event.getTransactionId().toString(), event);
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        send(PAYMENT_FAILED_TOPIC, event.getTransactionId().toString(), event);
    }

    private void send(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka publish failed for topic " + topic, ex);
        }
    }
}
