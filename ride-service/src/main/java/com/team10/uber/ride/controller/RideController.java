package com.team10.uber.ride.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.team10.uber.contracts.dto.DriverRideSummaryDTO;
import com.team10.uber.contracts.dto.RideDTO;
import com.team10.uber.ride.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team10.uber.ride.dto.DriverRecommendationDTO;
import com.team10.uber.ride.dto.FareEstimateDTO;
import com.team10.uber.ride.dto.FareEstimateRequestDTO;
import com.team10.uber.ride.dto.RideAnalyticsDTO;
import com.team10.uber.ride.dto.RideDetailsDTO;
import com.team10.uber.ride.dto.RideStopRequestDTO;
import com.team10.uber.ride.dto.RideWithStopsDTO;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.service.RecommendationService;
import com.team10.uber.ride.service.RideService;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;
    private final RecommendationService recommendationService;

    public RideController(RideService rideService, RecommendationService recommendationService) {
        this.rideService = rideService;
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public Ride createRide(@RequestBody Ride ride) {
        return rideService.createRide(ride);
    }

    @GetMapping
    public List<Ride> getAllRides() {
        return rideService.getAllRides();
    }

    @GetMapping("/driver/{driverId}/summary")
    public DriverRideSummaryDTO getDriverRideSummaryForFeign(
            @PathVariable Long driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return rideService.getDriverRideSummary(driverId, startDate, endDate);
    }

    @GetMapping("/driver/{driverId}/active-count")
    public int getDriverActiveRideCount(@PathVariable Long driverId) {
        return rideService.getDriverActiveRideCount(driverId);
    }

    @GetMapping("/driver/{driverId}/completed-count")
    public long getDriverCompletedRideCount(@PathVariable Long driverId) {
        return rideService.getDriverCompletedRideCount(driverId);
    }

    @GetMapping("/{id}")
    public RideDTO getRideById(@PathVariable Long id) {
        return rideService.getRideDto(id);
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
    @GetMapping("/search")
    public List<Ride> getRidesByStatusAndDateRange(
            @RequestParam(required = false, name = "status") RideStatus status,
            @RequestParam(required = false, name = "startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false, name = "endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, name = "userId") Long userId) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end   = endDate != null ? endDate.atTime(23, 59, 59) : null;
        return rideService.getRidesByStatusAndDateRange(status, start, end, userId);
    }

    @PutMapping("/{rideId}/assign")
    public Ride assignDriver(@PathVariable Long rideId, @RequestParam Long driverId) {
        return rideService.assignDriver(rideId, driverId);
    }

    @GetMapping("{rideId}/details")
    public RideDetailsDTO GetRideDetailsWithStops(@PathVariable Long rideId){
        return rideService.GetRideDetailsWithStops(rideId);
    }
    @GetMapping("/analytics/dashboard")
    public RideAnalyticsDashboardDTO getRideAnalyticsDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request
    ) {
        rideService.validateDashboardDateRange(startDate, endDate);

        RideAnalyticsDashboardDTO dto = rideService.getRideAnalyticsDashboard(startDate, endDate);

        Object userIdAttr = request.getAttribute("userId");
        Long userId = (userIdAttr instanceof Long l) ? l
                : (userIdAttr instanceof Number n) ? n.longValue()
                : null;
        rideService.logAnalyticsViewed(startDate, endDate, userId);

        return dto;
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<DriverRecommendationDTO>> getDriverRecommendations(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int limit,
            HttpServletRequest request) {

        Long requesterId = (Long) request.getAttribute("userId");
        String requesterRole = (String) request.getAttribute("userRole");

        if (!userId.equals(requesterId) && !"ADMIN".equals(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(recommendationService.getDriverRecommendations(userId, limit));
    }

    @PostMapping("/{rideId}/record-interaction")
    public String recordInteraction(@PathVariable Long rideId) {
        return rideService.recordInteraction(rideId);

    }
}