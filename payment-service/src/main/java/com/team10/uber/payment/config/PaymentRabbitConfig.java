package com.team10.uber.payment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRabbitConfig {

    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String ROUTING_KEY_COMPLETED = "payment.completed";
    public static final String ROUTING_KEY_FAILED = "payment.failed";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }
}
