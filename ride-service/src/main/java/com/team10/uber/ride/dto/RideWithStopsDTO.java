package com.team10.uber.ride.dto;

import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class RideWithStopsDTO {

    private Long id;
    private Long userId;
    private Long driverId;
    private RideStatus status;
    private Double fare;
    private Map<String, Object> metadata;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private List<RideStop> stops;

    public RideWithStopsDTO(Ride ride, List<RideStop> stops) {
        this.id = ride.getId();
        this.userId = ride.getUserId();
        this.driverId = ride.getDriverId();
        this.status = ride.getStatus();
        this.fare = ride.getFare();
        this.metadata = ride.getMetadata();
        this.requestedAt = ride.getRequestedAt();
        this.completedAt = ride.getCompletedAt();
        this.stops = stops;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getDriverId() { return driverId; }
    public RideStatus getStatus() { return status; }
    public Double getFare() { return fare; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public List<RideStop> getStops() { return stops; }
}