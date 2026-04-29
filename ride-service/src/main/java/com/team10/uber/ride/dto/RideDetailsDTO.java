package com.team10.uber.ride.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.enums.StopStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;

public class RideDetailsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("rideId")
    private Long id;

    private Long userId;
    private Long driverId;
    private RideStatus status;
    private Double fare;
    private Map<String, Object> metadata;
    private List<RideStop> stops;
    private Long totalStops;
    private Long completedStops;

    public RideDetailsDTO() {
    }

    public RideDetailsDTO(Ride ride, List<RideStop> stops) {
        this.id = ride.getId();
        this.userId = ride.getUserId();
        this.driverId = ride.getDriverId();
        this.status = ride.getStatus();
        this.fare = ride.getFare();
        this.metadata = ride.getMetadata() != null ? ride.getMetadata() : new HashMap<>();
        this.stops = stops;
        this.totalStops = (long) stops.size();
        this.completedStops = stops.stream()
                .filter(s -> s.getStatus() == StopStatus.REACHED)
                .count();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public RideStatus getStatus() {
        return status;
    }

    public void setStatus(RideStatus status) {
        this.status = status;
    }

    public Double getFare() {
        return fare;
    }

    public void setFare(Double fare) {
        this.fare = fare;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public List<RideStop> getStops() {
        return stops;
    }

    public void setStops(List<RideStop> stops) {
        this.stops = stops;
    }

    public Long getTotalStops() {
        return totalStops;
    }

    public void setTotalStops(Long totalStops) {
        this.totalStops = totalStops;
    }

    public Long getCompletedStops() {
        return completedStops;
    }

    public void setCompletedStops(Long completedStops) {
        this.completedStops = completedStops;
    }
}