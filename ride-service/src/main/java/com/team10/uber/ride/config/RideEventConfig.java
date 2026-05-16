package com.team10.uber.ride.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RideEventConfig {

    // ── ride.events (producer) ────────────────────────────────────
    public static final String RIDE_EXCHANGE             = "ride.events";
    public static final String ROUTING_KEY_RIDE_PLACED   = "ride.placed";
    public static final String ROUTING_KEY_RIDE_COMPLETED = "ride.completed";
    public static final String ROUTING_KEY_RIDE_CANCELLED = "ride.cancelled";

    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange(RIDE_EXCHANGE);
    }

    // ── payment.events (consumer) — ride.saga-feedback queue ─────
    public static final String PAYMENT_EXCHANGE              = "payment.events";
    public static final String SAGA_FEEDBACK_QUEUE           = "ride.saga-feedback";
    public static final String SAGA_FEEDBACK_DLQ             = "ride.saga-feedback.dlq";
    public static final String SAGA_FEEDBACK_DLQ_EXCHANGE    = "ride.saga-feedback.dlq.exchange";

    @Bean TopicExchange paymentEventsExchange() { return new TopicExchange(PAYMENT_EXCHANGE); }

    @Bean TopicExchange sagaFeedbackDlqExchange() { return new TopicExchange(SAGA_FEEDBACK_DLQ_EXCHANGE); }

    @Bean Queue sagaFeedbackDlq() { return QueueBuilder.durable(SAGA_FEEDBACK_DLQ).build(); }

    @Bean
    Binding sagaFeedbackDlqBinding(Queue sagaFeedbackDlq, TopicExchange sagaFeedbackDlqExchange) {
        return BindingBuilder.bind(sagaFeedbackDlq).to(sagaFeedbackDlqExchange).with("#");
    }

    @Bean
    Queue sagaFeedbackQueue() {
        return QueueBuilder.durable(SAGA_FEEDBACK_QUEUE)
                .withArgument("x-dead-letter-exchange", SAGA_FEEDBACK_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SAGA_FEEDBACK_DLQ)
                .build();
    }

    @Bean Binding paymentInitiatedBinding(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.initiated");
    }
    @Bean Binding paymentCompletedBinding(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.completed");
    }
    @Bean Binding paymentFailedBinding(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.failed");
    }
    @Bean Binding paymentRefundedBinding(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.refunded");
    }

    // ── user.events (consumer) — ride.user-audit queue ───────────
    public static final String USER_EXCHANGE           = "user.events";
    public static final String USER_AUDIT_QUEUE        = "ride.user-audit";
    public static final String USER_AUDIT_DLQ          = "ride.user-audit.dlq";
    public static final String USER_AUDIT_DLQ_EXCHANGE = "ride.user-audit.dlq.exchange";

    @Bean TopicExchange userEventsExchange() { return new TopicExchange(USER_EXCHANGE); }

    @Bean TopicExchange userAuditDlqExchange() { return new TopicExchange(USER_AUDIT_DLQ_EXCHANGE); }

    @Bean Queue userAuditDlq() { return QueueBuilder.durable(USER_AUDIT_DLQ).build(); }

    @Bean
    Binding userAuditDlqBinding(Queue userAuditDlq, TopicExchange userAuditDlqExchange) {
        return BindingBuilder.bind(userAuditDlq).to(userAuditDlqExchange).with("#");
    }

    @Bean
    Queue userAuditQueue() {
        return QueueBuilder.durable(USER_AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", USER_AUDIT_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", USER_AUDIT_DLQ)
                .build();
    }

    @Bean Binding userRegisteredBinding(Queue userAuditQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userAuditQueue).to(userEventsExchange).with("user.registered");
    }
    @Bean Binding userDeactivatedBinding(Queue userAuditQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userAuditQueue).to(userEventsExchange).with("user.deactivated");
    }

    // ── Jackson message converter (shared by template + listeners) ─
    @Bean
    MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.team10.uber.contracts.events");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
