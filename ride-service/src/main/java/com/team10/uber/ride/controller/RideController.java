package com.team10.uber.ride.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.team10.uber.ride.enums.RideStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team10.uber.ride.dto.RideStopRequestDTO;
import com.team10.uber.ride.dto.RideWithStopsDTO;
import com.team10.uber.ride.dto.FareEstimateDTO;
import com.team10.uber.ride.dto.FareEstimateRequestDTO;
import com.team10.uber.ride.dto.RideAnalyticsDTO;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.service.RideService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public Ride createRide(@RequestBody Ride ride) {
        return rideService.createRide(ride);
    }

    @GetMapping
    public List<Ride> getAllRides() {
        return rideService.getAllRides();
    }

    @GetMapping("/{id}")
    public Ride getRideById(@PathVariable Long id) {
        return rideService.getRideById(id);
    }

    @PutMapping("/{id}")
    public Ride updateRide(@PathVariable Long id, @RequestBody Ride updatedRide) {
        return rideService.updateRide(id, updatedRide);
    }

    @PutMapping("/{id}/cancel")
    public Ride cancelRide(@PathVariable Long id) {
        return rideService.cancelRide(id);
    }

    @DeleteMapping("/{id}")
    public String deleteRide(@PathVariable Long id) {
        rideService.deleteRide(id);
        return "Ride deleted successfully";
    }
     @PutMapping("/{id}/complete")
    public Ride completeRide(@PathVariable Long id) {
        return rideService.completeRide(id);
    }


    @PostMapping("/{rideId}/stops")
    public RideWithStopsDTO addStops(@PathVariable Long rideId, @RequestBody List<RideStopRequestDTO> stops) {
        return rideService.addStops(rideId, stops);
    }
  
    @GetMapping("/metadata/search")
    public List<Ride> filterByMetadata(@RequestParam String key, @RequestParam String value) {
        return rideService.filterByMetadata(key, value);
    }
  
    @PostMapping("/estimate")
    public FareEstimateDTO getFareEstimate(@RequestBody FareEstimateRequestDTO request) {
        return rideService.getFareEstimate(request);
    }
  
    @GetMapping("/analytics")
    public RideAnalyticsDTO getRideAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return rideService.getRideAnalytics(startDate, endDate);
    }
    @GetMapping("/search/")
    public List<Ride> GetRidesByStatusandDateRange(
            @RequestParam(required = false,name = "status") RideStatus status,
            @RequestParam(name = "startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end   = endDate.atTime(23, 59, 59);
        return rideService.GetRidesByStatusandDateRange(status, start, end);
    }
}
