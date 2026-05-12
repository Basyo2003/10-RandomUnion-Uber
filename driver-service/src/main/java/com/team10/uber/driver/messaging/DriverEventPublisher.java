package com.team10.uber.driver.messaging;

import com.team10.uber.contracts.events.DriverDocumentVerifiedEvent;
import com.team10.uber.contracts.events.DriverRatedEvent;
import com.team10.uber.contracts.events.DriverStatusChangedEvent;
import com.team10.uber.driver.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DriverEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DriverEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DriverEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishStatusChanged(Long driverId, String oldStatus, String newStatus) {
        var event = new DriverStatusChangedEvent(driverId, oldStatus, newStatus);
        rabbitTemplate.convertAndSend(RabbitMQConfig.DRIVER_EVENTS_EXCHANGE, "driver.status-changed", event);
        log.debug("Published driver.status-changed for driver {}: {} → {}", driverId, oldStatus, newStatus);
    }

    public void publishRated(Long driverId, Long rideId, Double rating, Long userId) {
        var event = new DriverRatedEvent(driverId, rideId, rating, userId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.DRIVER_EVENTS_EXCHANGE, "driver.rated", event);
        log.debug("Published driver.rated for driver {} ride {} rating {}", driverId, rideId, rating);
    }

    public void publishDocumentVerified(Long driverId, Long documentId, Long verifiedBy) {
        var event = new DriverDocumentVerifiedEvent(driverId, documentId, verifiedBy);
        rabbitTemplate.convertAndSend(RabbitMQConfig.DRIVER_EVENTS_EXCHANGE, "driver.document.verified", event);
        log.debug("Published driver.document.verified for driver {} doc {}", driverId, documentId);
    }
}
