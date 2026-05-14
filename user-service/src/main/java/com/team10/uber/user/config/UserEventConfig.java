package com.team10.uber.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventConfig {

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String RIDE_EVENTS_EXCHANGE = "ride.events";

    public static final String USER_RIDE_QUEUE = "user.ride.saga-listener";
    public static final String USER_RIDE_DLQ = "user.ride.saga-listener.dlq";
    public static final String USER_RIDE_DLX = "user.ride.saga-listener.dlx";
    public static final String USER_RIDE_DLQ_ROUTING_KEY = "user.ride.saga-listener.dlq";

    public static final String RIDE_COMPLETED_ROUTING_KEY = "ride.completed";
    public static final String RIDE_CANCELLED_ROUTING_KEY = "ride.cancelled";

    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String USER_DEACTIVATED_ROUTING_KEY = "user.deactivated";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange(RIDE_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange userRideDeadLetterExchange() {
        return new TopicExchange(USER_RIDE_DLX);
    }

    @Bean
    public Queue userRideSagaQueue() {
        return QueueBuilder.durable(USER_RIDE_QUEUE)
                .deadLetterExchange(USER_RIDE_DLX)
                .deadLetterRoutingKey(USER_RIDE_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue userRideSagaDeadLetterQueue() {
        return QueueBuilder.durable(USER_RIDE_DLQ).build();
    }

    @Bean
    public Binding rideCompletedBinding() {
        return BindingBuilder.bind(userRideSagaQueue())
                .to(rideEventsExchange())
                .with(RIDE_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding rideCancelledBinding() {
        return BindingBuilder.bind(userRideSagaQueue())
                .to(rideEventsExchange())
                .with(RIDE_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public Binding userRideSagaDeadLetterBinding() {
        return BindingBuilder.bind(userRideSagaDeadLetterQueue())
                .to(userRideDeadLetterExchange())
                .with(USER_RIDE_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
