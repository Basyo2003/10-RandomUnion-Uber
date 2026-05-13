package com.team10.uber.driver.messaging;

import com.team10.uber.contracts.events.DriverDocumentVerifiedEvent;
import com.team10.uber.contracts.events.DriverRatedEvent;
import com.team10.uber.contracts.events.DriverStatusChangedEvent;
import com.team10.uber.driver.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.*;

class DriverEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private DriverEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new DriverEventPublisher(rabbitTemplate);
    }

    // ── driver.status-changed (S2-F4) ────────────────────────────────────────

    @Test
    void publishStatusChanged_sendsToDriverEventsExchangeWithCorrectRoutingKey() {
        publisher.publishStatusChanged(1L, "AVAILABLE", "OFFLINE");

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.DRIVER_EVENTS_EXCHANGE,
                "driver.status-changed",
                new DriverStatusChangedEvent(1L, "AVAILABLE", "OFFLINE")
        );
    }

    @Test
    void publishStatusChanged_payloadContainsOldAndNewStatus() {
        publisher.publishStatusChanged(7L, "BUSY", "AVAILABLE");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.DRIVER_EVENTS_EXCHANGE),
                eq("driver.status-changed"),
                eq(new DriverStatusChangedEvent(7L, "BUSY", "AVAILABLE"))
        );
    }

    // ── driver.rated (S2-F7) ─────────────────────────────────────────────────

    @Test
    void publishDriverRated_sendsToDriverEventsExchangeWithCorrectRoutingKey() {
        publisher.publishDriverRated(1L, 10L, 5.0, 7L);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.DRIVER_EVENTS_EXCHANGE,
                "driver.rated",
                new DriverRatedEvent(1L, 10L, 5.0, 7L)
        );
    }

    @Test
    void publishDriverRated_payloadContainsAllFields() {
        publisher.publishDriverRated(2L, 20L, 4.0, 99L);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.DRIVER_EVENTS_EXCHANGE),
                eq("driver.rated"),
                eq(new DriverRatedEvent(2L, 20L, 4.0, 99L))
        );
    }

    // ── driver.document.verified (S2-F8) ─────────────────────────────────────

    @Test
    void publishDocumentVerified_sendsToDriverEventsExchangeWithCorrectRoutingKey() {
        publisher.publishDocumentVerified(1L, 10L, 3L);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.DRIVER_EVENTS_EXCHANGE,
                "driver.document.verified",
                new DriverDocumentVerifiedEvent(1L, 10L, 3L)
        );
    }

    @Test
    void publishDocumentVerified_payloadContainsAllFields() {
        publisher.publishDocumentVerified(5L, 42L, 1L);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.DRIVER_EVENTS_EXCHANGE),
                eq("driver.document.verified"),
                eq(new DriverDocumentVerifiedEvent(5L, 42L, 1L))
        );
    }
}
