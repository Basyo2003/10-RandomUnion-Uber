package com.team10.uber.ride.dto;

import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;


import java.util.List;
import java.util.Map;
public class RideDetailsDTO {

    private Long id;
    private Long userId;
    private Long driverId;
    private RideStatus status;
    private Double fare;
    private Map<String, Object> metadata;
    private List<RideStop> stops;
    private Long totalStops;
    private Long completedStops;

    public RideDetailsDTO(Ride ride, List<RideStop> stops) {
        this.id = id;
        this.userId = userId;
        this.driverId = driverId;
        this.status = status;
        this.fare = fare;
        this.metadata = metadata;
        this.stops = stops;
        this.totalStops = totalStops;
        this.completedStops = completedStops;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public RideStatus getStatus() {
        return status;
    }

    public Double getFare() {
        return fare;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<RideStop> getStops() {
        return stops;
    }

    public Long getTotalStops() {
        return totalStops;
    }

    public Long getCompletedStops() {
        return completedStops;
    }
}