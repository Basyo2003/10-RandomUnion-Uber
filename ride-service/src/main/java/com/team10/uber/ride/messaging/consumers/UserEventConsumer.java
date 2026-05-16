package com.team10.uber.ride.messaging.consumers;

import com.team10.uber.contracts.events.UserDeactivatedEvent;
import com.team10.uber.contracts.events.UserRegisteredEvent;
import com.team10.uber.ride.config.RideEventConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = RideEventConfig.USER_AUDIT_QUEUE)
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    // §5 S3 Consumes — audit log only, no state mutation in ride-postgres
    @RabbitHandler
    public void onUserRegistered(UserRegisteredEvent event, Message message) {
        setupMdc("user.registered", String.valueOf(event.userId()), message);
        try {
            log.info("Consuming user.registered for userId={}", event.userId());
            log.info("audit user.registered userId={} email={} role={}", event.userId(), event.email(), event.role());
            log.info("Processed user.registered for userId={}", event.userId());
        } catch (Exception e) {
            log.error("Failed to process user.registered: {}", e.getMessage());
            throw e;
        } finally {
            clearMdc();
        }
    }

    // S1-F4's Feign pre-check guarantees zero active rides at deactivation time,
    // so no compensation is needed here — audit only.
    @RabbitHandler
    public void onUserDeactivated(UserDeactivatedEvent event, Message message) {
        setupMdc("user.deactivated", String.valueOf(event.userId()), message);
        try {
            log.info("Consuming user.deactivated for userId={}", event.userId());
            log.info("audit user.deactivated userId={}", event.userId());
            log.info("Processed user.deactivated for userId={}", event.userId());
        } catch (Exception e) {
            log.error("Failed to process user.deactivated: {}", e.getMessage());
            throw e;
        } finally {
            clearMdc();
        }
    }

    private void setupMdc(String routingKey, String userId, Message message) {
        String correlationId = message.getMessageProperties().getHeader("X-Correlation-ID");
        if (correlationId != null) MDC.put("correlationId", correlationId);
        MDC.put("routingKey", routingKey);
        MDC.put("userId", userId);
    }

    private void clearMdc() {
        MDC.remove("correlationId");
        MDC.remove("routingKey");
        MDC.remove("userId");
    }
}
