package com.team10.uber.contracts.feign;

import com.team10.uber.contracts.dto.RideSummaryDTO;
import com.team10.uber.contracts.dto.DriverRideSummaryDTO;
import com.team10.uber.contracts.dto.RideDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ride-service", url = "${feign.ride-service.url}")
public interface RideServiceClient {

    @GetMapping("/api/rides/user/{userId}/summary")
    RideSummaryDTO getUserRideSummary(@PathVariable Long userId);

    @GetMapping("/api/rides/user/{userId}/active-count")
    int getActiveRideCount(@PathVariable Long userId);

    @GetMapping("/api/rides/user/{userId}/completed-count")
    long getCompletedRideCount(@PathVariable Long userId);

    @GetMapping("/api/rides/driver/{driverId}/summary")
    DriverRideSummaryDTO getDriverRideSummary(
        @PathVariable Long driverId,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    );

    @GetMapping("/api/rides/driver/{driverId}/active-count")
    int getDriverActiveRideCount(@PathVariable Long driverId);

    @GetMapping("/api/rides/driver/{driverId}/completed-count")
    long getDriverCompletedRideCount(@PathVariable Long driverId);

    @GetMapping("/api/rides/{rideId}")
    RideDTO getRide(@PathVariable Long rideId);
}