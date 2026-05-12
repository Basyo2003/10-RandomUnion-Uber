package com.team10.uber.driver.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String DRIVER_EVENTS_EXCHANGE  = "driver.events";
    public static final String RIDE_EVENTS_EXCHANGE    = "ride.events";

    public static final String RIDE_SAGA_QUEUE  = "driver.ride.saga-listener";
    public static final String RIDE_SAGA_DLQ    = "driver.ride.saga-listener.dlq";
    public static final String RIDE_SAGA_DLX    = "driver.ride.saga-listener.dlx";

    // ── Exchanges ────────────────────────────────────────────────────────────

    /** Producer side: driver-service publishes driver.* events here. */
    @Bean
    public TopicExchange driverEventsExchange() {
        return new TopicExchange(DRIVER_EVENTS_EXCHANGE);
    }

    /** Consumer side: ride-service publishes ride.* events here. Spring deduplicates if both sides declare it. */
    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange(RIDE_EVENTS_EXCHANGE);
    }

    /** Dead-letter exchange for the saga listener queue. */
    @Bean
    public TopicExchange rideSagaDlx() {
        return new TopicExchange(RIDE_SAGA_DLX);
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue rideSagaDlq() {
        return QueueBuilder.durable(RIDE_SAGA_DLQ).build();
    }

    @Bean
    public Queue rideSagaQueue() {
        return QueueBuilder.durable(RIDE_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", RIDE_SAGA_DLX)
                .withArgument("x-dead-letter-routing-key", RIDE_SAGA_DLQ)
                .build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding ridePlacedBinding(Queue rideSagaQueue, TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(rideSagaQueue).to(rideEventsExchange).with("ride.placed");
    }

    @Bean
    public Binding rideCompletedBinding(Queue rideSagaQueue, TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(rideSagaQueue).to(rideEventsExchange).with("ride.completed");
    }

    @Bean
    public Binding rideCancelledBinding(Queue rideSagaQueue, TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(rideSagaQueue).to(rideEventsExchange).with("ride.cancelled");
    }

    @Bean
    public Binding rideSagaDlqBinding(Queue rideSagaDlq, TopicExchange rideSagaDlx) {
        return BindingBuilder.bind(rideSagaDlq).to(rideSagaDlx).with(RIDE_SAGA_DLQ);
    }

    // ── Converter + Template ─────────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
