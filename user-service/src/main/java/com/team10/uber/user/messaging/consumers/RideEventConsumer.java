package com.team10.uber.user.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.uber.user.config.UserEventConfig;
import com.team10.uber.user.messaging.events.RideCancelledEvent;
import com.team10.uber.user.messaging.events.RideCompletedEvent;
import com.team10.uber.user.service.UserRideStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final UserRideStatsService userRideStatsService;

    public RideEventConsumer(ObjectMapper objectMapper, UserRideStatsService userRideStatsService) {
        this.objectMapper = objectMapper;
        this.userRideStatsService = userRideStatsService;
    }

    @RabbitListener(queues = UserEventConfig.USER_RIDE_QUEUE)
    public void consume(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String correlationId = getHeader(message, "X-Correlation-ID");
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            putMdc("correlationId", correlationId);
            putMdc("routingKey", routingKey);
            log.info("Consuming {} for payload={}", routingKey, payload);

            if (UserEventConfig.RIDE_COMPLETED_ROUTING_KEY.equals(routingKey)) {
                RideCompletedEvent event = readPayload(payload, RideCompletedEvent.class);
                putMdc("userId", event.userId());
                putMdc("rideId", event.rideId());
                userRideStatsService.applyRideCompleted(event);
                log.info("Processed {} for rideId={}", routingKey, event.rideId());
                return;
            }

            if (UserEventConfig.RIDE_CANCELLED_ROUTING_KEY.equals(routingKey)) {
                RideCancelledEvent event = readPayload(payload, RideCancelledEvent.class);
                putMdc("userId", event.userId());
                putMdc("rideId", event.rideId());
                userRideStatsService.applyRideCancelled(event);
                log.info("Processed {} for rideId={}", routingKey, event.rideId());
                return;
            }

            log.warn("Ignoring unsupported routing key {}", routingKey);
        } catch (Exception exception) {
            log.error("Failed to process {}: {}", routingKey, exception.getMessage());
            throw new IllegalStateException(exception);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("routingKey");
            MDC.remove("userId");
            MDC.remove("rideId");
        }
    }

    private <T> T readPayload(String payload, Class<T> type) throws IOException {
        return objectMapper.readValue(payload, type);
    }

    private String getHeader(Message message, String header) {
        Object value = message.getMessageProperties().getHeaders().get(header);
        return value == null ? null : value.toString();
    }

    private void putMdc(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }
}
