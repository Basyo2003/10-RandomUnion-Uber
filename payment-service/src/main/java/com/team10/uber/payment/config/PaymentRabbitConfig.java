package com.team10.uber.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRabbitConfig {

    // ── Payment exchange (producer) ─────────────────────────────────────────
    public static final String PAYMENT_EXCHANGE        = "payment.events";
    public static final String ROUTING_KEY_INITIATED   = "payment.initiated";
    public static final String ROUTING_KEY_COMPLETED   = "payment.completed";
    public static final String ROUTING_KEY_FAILED      = "payment.failed";
    public static final String ROUTING_KEY_REFUNDED    = "payment.refunded";

    // ── Ride exchange (consumed by payment-service) ─────────────────────────
    public static final String RIDE_EXCHANGE            = "ride.events";
    public static final String SAGA_QUEUE               = "payment.saga-listener";
    public static final String SAGA_DLQ                 = "payment.saga-listener.dlq";
    public static final String SAGA_DLX                 = "payment.saga-listener.dlx";

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    // Reference to ride.events exchange so Spring registers it before the binding
    @Bean
    public TopicExchange rideExchangeRef() {
        return new TopicExchange(RIDE_EXCHANGE, true, false);
    }

    // Dead-letter exchange
    @Bean
    public TopicExchange sagaDlx() {
        return new TopicExchange(SAGA_DLX, true, false);
    }

    // Dead-letter queue
    @Bean
    public Queue sagaDlq() {
        return QueueBuilder.durable(SAGA_DLQ).build();
    }

    // Consumer queue with DLQ routing via x-dead-letter-exchange
    @Bean
    public Queue sagaQueue() {
        return QueueBuilder.durable(SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", SAGA_DLX)
                .withArgument("x-dead-letter-routing-key", SAGA_DLQ)
                .build();
    }

    // Bind saga queue to ride.events for ride.completed and ride.cancelled
    @Bean
    public Binding sagaCompletedBinding() {
        return BindingBuilder.bind(sagaQueue())
                .to(rideExchangeRef())
                .with("ride.completed");
    }

    @Bean
    public Binding sagaCancelledBinding() {
        return BindingBuilder.bind(sagaQueue())
                .to(rideExchangeRef())
                .with("ride.cancelled");
    }
}
