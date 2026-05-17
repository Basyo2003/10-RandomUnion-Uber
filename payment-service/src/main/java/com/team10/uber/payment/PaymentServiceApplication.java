package com.team10.uber.payment;

import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableFeignClients(clients = {UserServiceClient.class, RideServiceClient.class, DriverServiceClient.class})
public class PaymentServiceApplication {

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

	

}
