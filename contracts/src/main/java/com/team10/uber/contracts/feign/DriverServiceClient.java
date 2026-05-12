package com.team10.uber.contracts.feign;

import com.team10.uber.contracts.dto.DriverDTO;
import com.team10.uber.contracts.dto.DriverAvailabilityDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "driver-service", url = "${feign.driver-service.url}")
public interface DriverServiceClient {

    @GetMapping("/api/drivers/{id}")
    DriverDTO getDriver(@PathVariable Long id);

    @GetMapping("/api/drivers/{id}/availability")
    DriverAvailabilityDTO getDriverAvailability(@PathVariable Long id);
}