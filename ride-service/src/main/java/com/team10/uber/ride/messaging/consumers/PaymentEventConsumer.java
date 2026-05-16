package com.team10.uber.ride.messaging.consumers;

import com.team10.uber.contracts.events.PaymentCompletedEvent;
import com.team10.uber.contracts.events.PaymentFailedEvent;
import com.team10.uber.contracts.events.PaymentInitiatedEvent;
import com.team10.uber.contracts.events.PaymentRefundedEvent;
import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.ride.config.RideEventConfig;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.messaging.publishers.RideEventPublisher;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RabbitListener(queues = RideEventConfig.SAGA_FEEDBACK_QUEUE)
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final RideRepository rideRepository;
    private final RideEventPublisher publisher;

    public PaymentEventConsumer(RideRepository rideRepository, RideEventPublisher publisher) {
        this.rideRepository = rideRepository;
        this.publisher = publisher;
    }

    @RabbitHandler
    @Transactional
    public void onPaymentInitiated(PaymentInitiatedEvent event, Message message) {
        setupMdc("payment.initiated", String.valueOf(event.rideId()), message);
        try {
            log.info("Consuming payment.initiated for ride={}", event.rideId());
            transitionStatus(event.rideId(), RideStatus.PAYMENT_PENDING, RideStatus.COMPLETED);
            log.info("Processed payment.initiated for ride={}", event.rideId());
        } catch (Exception e) {
            log.error("Failed to process payment.initiated: {}", e.getMessage());
            throw e;
        } finally {
            clearMdc();
        }
    }

    @RabbitHandler
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event, Message message) {
        setupMdc("payment.completed", String.valueOf(event.rideId()), message);
        try {
            log.info("Consuming payment.completed for ride={}", event.rideId());
            transitionStatus(event.rideId(), RideStatus.PAID, RideStatus.PAYMENT_PENDING);
            log.info("Processed payment.completed for ride={}", event.rideId());
        } catch (Exception e) {
            log.error("Failed to process payment.completed: {}", e.getMessage());
            throw e;
        } finally {
            clearMdc();
        }
    }

    // §8.2 compensation: payment failure → PAYMENT_FAILED + publish ride.cancelled
    @RabbitHandler
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event, Message message) {
        setupMdc("payment.failed", String.valueOf(event.rideId()), message);
        try {
            log.info("Consuming payment.failed for ride={} reason={}", event.rideId(), event.reason());
            Optional<Ride> opt = rideRepository.findById(event.rideId());
            if (opt.isEmpty()) {
                log.warn("payment.failed: ride {} not found", event.rideId());
                return;
            }
            Ride ride = opt.get();
            if (ride.getStatus() != RideStatus.PAYMENT_PENDING) {
                log.info("payment.failed: ride {} status={} — idempotent skip", ride.getId(), ride.getStatus());
                return;
            }
            RideStatus oldStatus = ride.getStatus();
            ride.setStatus(RideStatus.PAYMENT_FAILED);
            log.info("Ride {} transitioning {} → {}", ride.getId(), oldStatus, RideStatus.PAYMENT_FAILED);
            rideRepository.save(ride);
            log.info("Ride {} saved with status={}", ride.getId(), ride.getStatus());
            RideCancelledEvent cancelEvent = new RideCancelledEvent(
                    ride.getId(), ride.getUserId(), ride.getDriverId(), "Payment failed: " + event.reason());
            publisher.publishRideCancelled(cancelEvent);
            log.info("Published ride.cancelled for ride={}", ride.getId());
            log.info("Processed payment.failed for ride={}", event.rideId());
        } catch (Exception e) {
            log.error("Failed to process payment.failed: {}", e.getMessage());
            throw e;
        } finally {
            clearMdc();
        }
    }

    @RabbitHandler
    @Transactional
    public void onPaymentRefunded(PaymentRefundedEvent event, Message message) {
        setupMdc("payment.refunded", String.valueOf(event.rideId()), message);
        try {
            log.info("Consuming payment.refunded for ride={}", event.rideId());
            transitionStatus(event.rideId(), RideStatus.REFUNDED, RideStatus.CANCELLED, RideStatus.PAYMENT_FAILED);
            log.info("Processed payment.refunded for ride={}", event.rideId());
        } catch (Exception e) {
            log.error("Failed to process payment.refunded: {}", e.getMessage());
            throw e;
        } finally {
            clearMdc();
        }
    }

    // §16 R11 — idempotency: only mutate when ride is in an expected preceding status
    private void transitionStatus(Long rideId, RideStatus target, RideStatus... from) {
        Optional<Ride> opt = rideRepository.findById(rideId);
        if (opt.isEmpty()) {
            log.warn("transitionStatus: ride {} not found", rideId);
            return;
        }
        Ride ride = opt.get();
        for (RideStatus expected : from) {
            if (ride.getStatus() == expected) {
                log.info("Ride {} transitioning {} → {}", rideId, expected, target);
                ride.setStatus(target);
                rideRepository.save(ride);
                log.info("Ride {} saved with status={}", rideId, target);
                return;
            }
        }
        log.info("ride {} status={} not in expected set — idempotent skip", rideId, ride.getStatus());
    }

    private void setupMdc(String routingKey, String rideId, Message message) {
        String correlationId = message.getMessageProperties().getHeader("X-Correlation-ID");
        if (correlationId != null) MDC.put("correlationId", correlationId);
        MDC.put("routingKey", routingKey);
        MDC.put("rideId", rideId);
    }

    private void clearMdc() {
        MDC.remove("correlationId");
        MDC.remove("routingKey");
        MDC.remove("rideId");
    }
}
