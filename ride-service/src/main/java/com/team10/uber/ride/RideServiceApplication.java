package com.team10.uber.ride;

import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.feign.LocationServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableFeignClients(clients = {DriverServiceClient.class, UserServiceClient.class, LocationServiceClient.class})
@EnableAsync
public class RideServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RideServiceApplication.class, args);
	}

}
