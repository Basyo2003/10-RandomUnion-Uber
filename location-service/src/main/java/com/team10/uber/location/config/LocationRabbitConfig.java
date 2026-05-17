package com.team10.uber.location.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocationRabbitConfig {

    public static final String RIDE_EXCHANGE = "ride.events";
    public static final String LOCATION_EXCHANGE = "location.events";

    public static final String LOCATION_RIDE_QUEUE = "location.ride.saga-listener";
    public static final String LOCATION_RIDE_DLQ = "location.ride.saga-listener.dlq";
    public static final String LOCATION_RIDE_DLX = "location.ride.saga-listener.dlx";

    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange(RIDE_EXCHANGE);
    }

    @Bean
    public TopicExchange locationEventsExchange() {
        return new TopicExchange(LOCATION_EXCHANGE);
    }

    @Bean
    public DirectExchange locationRideDeadLetterExchange() {
        return new DirectExchange(LOCATION_RIDE_DLX);
    }

    @Bean
    public Queue locationRideQueue() {
        return QueueBuilder.durable(LOCATION_RIDE_QUEUE)
                .withArgument("x-dead-letter-exchange", LOCATION_RIDE_DLX)
                .withArgument("x-dead-letter-routing-key", LOCATION_RIDE_DLQ)
                .build();
    }

    @Bean
    public Queue locationRideDlq() {
        return QueueBuilder.durable(LOCATION_RIDE_DLQ).build();
    }

    @Bean
    public Binding bindRideCompleted() {
        return BindingBuilder.bind(locationRideQueue())
                .to(rideEventsExchange())
                .with("ride.completed");
    }

    @Bean
    public Binding bindRideCancelled() {
        return BindingBuilder.bind(locationRideQueue())
                .to(rideEventsExchange())
                .with("ride.cancelled");
    }

    @Bean
    public Binding bindLocationRideDlq() {
        return BindingBuilder.bind(locationRideDlq())
                .to(locationRideDeadLetterExchange())
                .with(LOCATION_RIDE_DLQ);
    }
}