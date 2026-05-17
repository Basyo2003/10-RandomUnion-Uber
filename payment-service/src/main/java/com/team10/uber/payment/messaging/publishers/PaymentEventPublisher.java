package com.team10.uber.payment.messaging.publishers;

import com.team10.uber.payment.config.PaymentRabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team10.uber.contracts.events.PaymentInitiatedEvent;
import com.team10.uber.contracts.events.PaymentCompletedEvent;
import com.team10.uber.contracts.events.PaymentFailedEvent;
import com.team10.uber.contracts.events.PaymentRefundedEvent;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentInitiated(PaymentInitiatedEvent event) {
        publish("payment.initiated", event, event.paymentId(), event.rideId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        publish("payment.completed", event, event.paymentId(), event.rideId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        publish("payment.failed", event, event.paymentId(), event.rideId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentRefunded(PaymentRefundedEvent event) {
        publish("payment.refunded", event, event.paymentId(), event.rideId());
    }

    private void publish(String routingKey, Object event, Long paymentId, Long rideId) {
        try {
            MDC.put("routingKey", routingKey);
            MDC.put("paymentId", String.valueOf(paymentId));
            MDC.put("rideId", String.valueOf(rideId));
            
            rabbitTemplate.convertAndSend(PaymentRabbitConfig.PAYMENT_EXCHANGE, routingKey, event);
            log.info("Published {} for Payment={}", routingKey, paymentId);
        } finally {
            MDC.clear();
        }
    }
}