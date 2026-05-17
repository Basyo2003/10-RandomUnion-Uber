package com.team10.uber.payment.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.contracts.events.RideCompletedEvent;
import com.team10.uber.payment.config.PaymentRabbitConfig;
import com.team10.uber.payment.enums.PaymentMethod;
import com.team10.uber.payment.enums.PaymentStatus;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.repository.PaymentRepository;
import com.team10.uber.payment.strategy.RefundRequest;
import com.team10.uber.payment.strategy.RefundResult;
import com.team10.uber.payment.strategy.RefundStrategy;
import com.team10.uber.payment.strategy.RefundStrategySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team10.uber.contracts.events.PaymentInitiatedEvent;
import com.team10.uber.contracts.events.PaymentRefundedEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RideEventConsumer(PaymentRepository paymentRepository,
                             RabbitTemplate rabbitTemplate,
                             ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = PaymentRabbitConfig.SAGA_QUEUE)
    @Transactional
    public void handleRideEvent(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        MDC.put("routingKey", routingKey);
        log.info("Consuming {} from payment.saga-listener", routingKey);

        try {
            if ("ride.completed".equals(routingKey)) {
                RideCompletedEvent event = objectMapper.readValue(
                        message.getBody(), RideCompletedEvent.class);
                handleRideCompleted(event);
            } else if ("ride.cancelled".equals(routingKey)) {
                RideCancelledEvent event = objectMapper.readValue(
                        message.getBody(), RideCancelledEvent.class);
                handleRideCancelled(event);
            } else {
                log.warn("Unhandled routing key in payment.saga-listener: {}", routingKey);
            }
        } catch (Exception e) {
            log.error("Failed to process {}: {}", routingKey, e.getMessage());
            throw new RuntimeException(e); // Spring retries → DLQ after max-attempts
        } finally {
            MDC.remove("routingKey");
            MDC.remove("rideId");
        }
    }

    // §S5 Consumes: ride.completed → create PENDING Payment + publish payment.initiated
    private void handleRideCompleted(RideCompletedEvent event) {
        MDC.put("rideId", String.valueOf(event.rideId()));
        log.info("Consuming ride.completed for rideId={}", event.rideId());

        // Idempotency: skip if a payment already exists for this ride
        Optional<Payment> existing = paymentRepository.findByRideIdAndStatus(
                event.rideId(), PaymentStatus.PENDING);
        if (existing.isPresent()) {
            log.warn("Duplicate ride.completed for rideId={} — payment already PENDING, skipping", event.rideId());
            return;
        }

        Payment payment = new Payment();
        payment.setRideId(event.rideId());
        payment.setUserId(event.userId());
        payment.setAmount(event.fare() != null ? event.fare() : 0.0);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMethod(PaymentMethod.CREDIT_CARD); // overridden by S5-F4 when rider pays

        Payment saved = paymentRepository.save(payment);
        log.info("Payment {} saved with status=PENDING for rideId={}", saved.getId(), event.rideId());

        PaymentInitiatedEvent initiated = new PaymentInitiatedEvent(
                saved.getId(), event.rideId(), event.fare());
        rabbitTemplate.convertAndSend(
                PaymentRabbitConfig.PAYMENT_EXCHANGE,
                PaymentRabbitConfig.ROUTING_KEY_INITIATED,
                initiated);
        log.info("Published payment.initiated for paymentId={}, rideId={}", saved.getId(), event.rideId());
    }

    // §S5 Consumes: ride.cancelled → refund PENDING/COMPLETED payment + publish payment.refunded
    private void handleRideCancelled(RideCancelledEvent event) {
        MDC.put("rideId", String.valueOf(event.rideId()));
        log.info("Consuming ride.cancelled for rideId={}", event.rideId());

        Optional<Payment> targetOpt = paymentRepository.findByRideIdAndStatus(
                event.rideId(), PaymentStatus.PENDING);
        if (targetOpt.isEmpty()) {
            targetOpt = paymentRepository.findByRideIdAndStatus(
                    event.rideId(), PaymentStatus.COMPLETED);
        }
        if (targetOpt.isEmpty()) {
            log.info("No PENDING/COMPLETED payment for cancelled rideId={} — no-op", event.rideId());
            return;
        }

        Payment payment = targetOpt.get();

        // Idempotency: already refunded
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.warn("Payment {} already REFUNDED for rideId={} — skipping", payment.getId(), event.rideId());
            return;
        }

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setReason(event.reason() != null ? event.reason() : "ride_cancelled");
        refundRequest.setRefundSurge(true);

        RefundStrategySelector selector = new RefundStrategySelector();
        RefundStrategy strategy = selector.select(payment, refundRequest);
        RefundResult result = strategy.calculateRefund(payment, refundRequest);
        double refundAmount = result.getAmount() > 0 ? result.getAmount() : payment.getAmount();

        payment.setStatus(PaymentStatus.REFUNDED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundAmount", refundAmount);
        details.put("refundReason", refundRequest.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);
        Payment saved = paymentRepository.save(payment);
        log.info("Payment {} saved with status=REFUNDED for rideId={}", saved.getId(), event.rideId());

        PaymentRefundedEvent refunded = new PaymentRefundedEvent(
                saved.getId(), event.rideId(), refundAmount);
        rabbitTemplate.convertAndSend(
                PaymentRabbitConfig.PAYMENT_EXCHANGE,
                PaymentRabbitConfig.ROUTING_KEY_REFUNDED,
                refunded);
        log.info("Published payment.refunded for paymentId={}, rideId={}", saved.getId(), event.rideId());
    }
}
