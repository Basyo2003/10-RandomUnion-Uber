package com.team10.uber.user.messaging.publishers;

import com.team10.uber.user.config.UserEventConfig;
import com.team10.uber.user.messaging.events.UserDeactivatedEvent;
import com.team10.uber.user.messaging.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(UserRegisteredEvent event) {
        publish(UserEventConfig.USER_REGISTERED_ROUTING_KEY, event, "userId", event.userId());
    }

    public void publishUserDeactivated(UserDeactivatedEvent event) {
        publish(UserEventConfig.USER_DEACTIVATED_ROUTING_KEY, event, "userId", event.userId());
    }
//Published user.registered for userId=1
    private void publish(String routingKey, Object event, String entityName, Long entityId) {
        rabbitTemplate.convertAndSend(
                UserEventConfig.USER_EVENTS_EXCHANGE,
                routingKey,
                event,
                message -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        message.getMessageProperties().setHeader("X-Correlation-ID", correlationId);
                    }
                    message.getMessageProperties().setHeader("routingKey", routingKey);
                    return message;
                });
        log.info("Published {} for {}={}", routingKey, entityName, entityId);
    }
}
