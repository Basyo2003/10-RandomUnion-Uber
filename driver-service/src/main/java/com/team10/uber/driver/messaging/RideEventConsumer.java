package com.team10.uber.driver.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.contracts.events.RideCompletedEvent;
import com.team10.uber.contracts.events.RidePlacedEvent;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.config.RabbitMQConfig;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    private final DriverRepository driverRepository;
    private final ObjectMapper objectMapper;
    private final RedisCacheService cache;

    public RideEventConsumer(DriverRepository driverRepository, ObjectMapper objectMapper, RedisCacheService cache) {
        this.driverRepository = driverRepository;
        this.objectMapper = objectMapper;
        this.cache = cache;
    }

    @RabbitListener(queues = RabbitMQConfig.RIDE_SAGA_QUEUE)
    public void onRideEvent(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            switch (routingKey) {
                case "ride.placed"    -> handleRidePlaced(objectMapper.readValue(body, RidePlacedEvent.class));
                case "ride.completed" -> handleRideCompleted(objectMapper.readValue(body, RideCompletedEvent.class));
                case "ride.cancelled" -> handleRideCancelled(objectMapper.readValue(body, RideCancelledEvent.class));
                default -> log.warn("Unhandled routing key on ride-saga queue: {}", routingKey);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ride event [{}]: {}", routingKey, e.getMessage());
            // rethrow so Spring retries and routes to DLQ after max-attempts
            throw new RuntimeException("Deserialization failed for routing key: " + routingKey, e);
        }
    }

    // package-private so tests can call directly
    void handleRidePlaced(RidePlacedEvent event) {
        driverRepository.findById(event.driverId()).ifPresentOrElse(driver -> {
            if (driver.getStatus() == DriverStatus.BUSY) {
                // idempotent: concurrent assignment race — spec says no-op, do not DLQ
                log.warn("ride.placed: driver {} already BUSY — idempotent no-op (rideId={})",
                        event.driverId(), event.rideId());
                return;
            }
            driver.setStatus(DriverStatus.BUSY);
            driverRepository.save(driver);
            cache.evict("driver::" + event.driverId());
            log.debug("ride.placed: driver {} set to BUSY (rideId={})", event.driverId(), event.rideId());
        }, () -> log.warn("ride.placed: driver {} not found (rideId={})", event.driverId(), event.rideId()));
    }

    void handleRideCompleted(RideCompletedEvent event) {
        driverRepository.findById(event.driverId()).ifPresentOrElse(driver -> {
            driver.setStatus(DriverStatus.AVAILABLE);
            driver.setTotalRatings(driver.getTotalRatings() + 1);
            driverRepository.save(driver);
            cache.evict("driver::" + event.driverId());
            log.debug("ride.completed: driver {} set to AVAILABLE, totalRatings incremented (rideId={})",
                    event.driverId(), event.rideId());
        }, () -> log.warn("ride.completed: driver {} not found (rideId={})", event.driverId(), event.rideId()));
    }

    void handleRideCancelled(RideCancelledEvent event) {
        driverRepository.findById(event.driverId()).ifPresentOrElse(driver -> {
            driver.setStatus(DriverStatus.AVAILABLE);
            if (driver.getTotalRatings() > 0) {
                driver.setTotalRatings(driver.getTotalRatings() - 1);
            }
            driverRepository.save(driver);
            cache.evict("driver::" + event.driverId());
            log.debug("ride.cancelled: driver {} set to AVAILABLE, stats reversed (rideId={})",
                    event.driverId(), event.rideId());
        }, () -> log.warn("ride.cancelled: driver {} not found (rideId={})", event.driverId(), event.rideId()));
    }
}
