package com.team10.uber.contracts.feign;

import com.team10.uber.contracts.dto.LocationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "location-service", url = "${feign.location-service.url}")
public interface LocationServiceClient {

    @GetMapping("/api/locations/driver/{driverId}/recent")
    LocationDTO getRecentLocationForDriver(@PathVariable Long driverId);
}