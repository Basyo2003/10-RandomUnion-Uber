package com.team10.uber.user;

import com.team10.uber.contracts.feign.PaymentServiceClient;
import com.team10.uber.contracts.feign.RideServiceClient;
import org.springframework.boot.SpringApplication;
import com.team10.uber.user.config.FeignCorrelationConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(
		clients = {RideServiceClient.class, PaymentServiceClient.class},
		defaultConfiguration = FeignCorrelationConfig.class
)
@EnableCaching
public class UserServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}
}
