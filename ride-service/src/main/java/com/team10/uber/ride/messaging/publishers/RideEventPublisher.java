package com.team10.uber.ride.messaging.publishers;

import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.contracts.events.RideCompletedEvent;
import com.team10.uber.contracts.events.RidePlacedEvent;
import com.team10.uber.ride.config.RideEventConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RideEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RideEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RideEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishRidePlaced(RidePlacedEvent event) {
        sendAfterCommit(RideEventConfig.ROUTING_KEY_RIDE_PLACED, event);
    }

    public void publishRideCompleted(RideCompletedEvent event) {
        sendAfterCommit(RideEventConfig.ROUTING_KEY_RIDE_COMPLETED, event);
    }

    public void publishRideCancelled(RideCancelledEvent event) {
        sendAfterCommit(RideEventConfig.ROUTING_KEY_RIDE_CANCELLED, event);
    }

    // §2.11 — commit first, then publish. Registers send in AFTER_COMMIT phase
    // when called from inside a @Transactional boundary; publishes immediately otherwise.
    private void sendAfterCommit(String routingKey, Object payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(routingKey, payload);
                }
            });
        } else {
            send(routingKey, payload);
        }
    }

    private void send(String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(RideEventConfig.RIDE_EXCHANGE, routingKey, payload);
            log.info("event published exchange={} routingKey={}", RideEventConfig.RIDE_EXCHANGE, routingKey);
        } catch (Exception e) {
            log.error("failed to publish routingKey={}: {}", routingKey, e.getMessage());
        }
    }
}
